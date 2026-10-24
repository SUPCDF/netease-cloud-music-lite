package com.ncmusic.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.ncmusic.core.common.R
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import com.ncmusic.core.network.util.requireCode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 用户头像上传。
 *
 * 网易云的头像更新分两步：
 * 1. weapi/nos/token/alloc 申请 NOS 上传凭证；
 * 2. 把压缩后的 JPEG 原始字节 POST 到 NOS，再用返回的 docId 调
 *    weapi/user/avatar/upload/v1 正式更新头像。
 *
 * 图片在本地居中裁成正方形并压到 512px，避免直接上传几 MB 的原图。
 */
@Singleton
class AvatarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: NeteaseApi,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** NOS 是网易 CDN，不需要登录 Cookie，避免把 MUSIC_U 发给图片服务器 */
    private val nosClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .build()
    }

    suspend fun updateAvatar(uri: Uri): AppResult<String?> = apiCall(io) {
        val jpeg = loadAvatarJpeg(uri)
        val alloc = api.nosTokenAlloc(
            WeapiBody(
                jsonOf(
                    "bucket" to "yyimgs",
                    "ext" to "jpg",
                    "filename" to "avatar_${System.currentTimeMillis()}.jpg",
                    "local" to false,
                    "nos_product" to 0,
                    "return_body" to """{"code":200,"size":"$(ObjectSize)"}""",
                    "type" to "other",
                ),
            ),
        )
        requireCode(alloc.code, alloc.message)
        val uploadInfo = alloc.result
            ?: throw AppError.Api(-1, Str.res(R.string.avatar_update_failed))
        val objectKey = uploadInfo.objectKey?.takeIf { it.isNotBlank() }
            ?: throw AppError.Api(-1, Str.res(R.string.avatar_update_failed))
        val token = uploadInfo.token?.takeIf { it.isNotBlank() }
            ?: throw AppError.Api(-1, Str.res(R.string.avatar_update_failed))
        val docId = uploadInfo.docId?.takeIf { it.isNotBlank() }
            ?: throw AppError.Api(-1, Str.res(R.string.avatar_update_failed))

        uploadToNos(objectKey, token, jpeg)

        val resp = api.avatarUpload(WeapiBody(jsonOf("imgid" to docId)))
        requireCode(resp.code, resp.message ?: resp.msg ?: Str.res(R.string.avatar_update_failed))
        resp.url
    }

    private fun uploadToNos(objectKey: String, token: String, jpeg: ByteArray) {
        val request = Request.Builder()
            .url("https://nosup-hz1.127.net/yyimgs/$objectKey?offset=0&complete=true&version=1.0")
            .post(jpeg.toRequestBody(JPEG_MEDIA_TYPE))
            .header("x-nos-token", token)
            .build()
        nosClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AppError.Api(-1, Str.res(R.string.avatar_update_failed))
            }
        }
    }

    /** 读取相册图片，居中裁剪并压缩成适合上传的小 JPEG */
    private fun loadAvatarJpeg(uri: Uri): ByteArray {
        val original = try {
            decodeBitmap(context.contentResolver, uri)
        } catch (_: OutOfMemoryError) {
            throw AppError.Api(-1, Str.res(R.string.avatar_image_invalid))
        } catch (_: Exception) {
            throw AppError.Api(-1, Str.res(R.string.avatar_image_invalid))
        } ?: throw AppError.Api(-1, Str.res(R.string.avatar_image_invalid))

        val side = min(original.width, original.height)
        if (side <= 0) {
            original.recycle()
            throw AppError.Api(-1, Str.res(R.string.avatar_image_invalid))
        }
        val left = (original.width - side) / 2
        val top = (original.height - side) / 2
        val square = if (left == 0 && top == 0 && side == original.width && side == original.height) {
            original
        } else {
            Bitmap.createBitmap(original, left, top, side, side)
        }
        val output = if (side > AVATAR_SIZE) {
            Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)
        } else {
            square
        }

        val bytes = ByteArrayOutputStream().use { out ->
            if (!output.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                throw AppError.Api(-1, Str.res(R.string.avatar_image_invalid))
            }
            out.toByteArray()
        }

        if (output !== square) output.recycle()
        if (square !== original) square.recycle()
        original.recycle()
        return bytes
    }

    private fun decodeBitmap(resolver: ContentResolver, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_DECODE_SIZE) {
                    val scale = MAX_DECODE_SIZE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeLegacyBitmap(resolver, uri)
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeLegacyBitmap(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_SIZE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return applyLegacyOrientation(resolver, uri, bitmap)
    }

    /** Android 9 以下 ImageDecoder 不可用，手动读取 EXIF 方向 */
    @Suppress("DEPRECATION")
    private fun applyLegacyOrientation(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (_: Exception) {
            null
        } ?: return bitmap

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
    }

    private companion object {
        const val AVATAR_SIZE = 512
        const val MAX_DECODE_SIZE = 1024
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
