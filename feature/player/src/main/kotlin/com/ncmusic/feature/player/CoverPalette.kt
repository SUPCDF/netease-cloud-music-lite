package com.ncmusic.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * 取封面主色调（用于「灵魂鼓点闪光」背景光晕）。
 *
 * 做法：用 Coil 把封面缩到 48x48 后求平均色，再抬高饱和度 / 亮度，
 * 避免封面偏灰时背景光晕看起来很脏。
 */
@Composable
fun rememberCoverColor(url: String?): Color {
    val context = LocalContext.current
    return produceState(Color.Unspecified, url) {
        value = if (url.isNullOrBlank()) {
            Color.Unspecified
        } else {
            extractCoverColor(context, url)
        }
    }.value
}

private suspend fun extractCoverColor(context: Context, url: String): Color {
    val bitmap = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(48, 48)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        (result as? SuccessResult)?.drawable?.let { drawable ->
            (drawable as? BitmapDrawable)?.bitmap?.let { bmp ->
                if (bmp.width > 0 && bmp.height > 0) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    null
                }
            }
        }
    }.getOrNull() ?: return Color.Unspecified

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()

    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 200) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        // 过暗 / 过亮（黑白边框、纯黑背景）不参与平均，避免色调被拉灰
        if (luminance < 24 || luminance > 236) continue
        red += r
        green += g
        blue += b
        count++
    }
    if (count == 0) return Color.Unspecified

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (red / count).toInt().coerceIn(0, 255),
            (green / count).toInt().coerceIn(0, 255),
            (blue / count).toInt().coerceIn(0, 255),
        ),
        hsv,
    )
    // 抬高饱和度与亮度，让光晕更「通透」
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    hsv[2] = hsv[2].coerceAtLeast(0.65f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 颜色变暗 / 变亮的小工具（光晕边缘过渡用） */
fun Color.scale(factor: Float): Color {
    val argb = toArgb()
    val r = ((argb shr 16) and 0xFF) * factor
    val g = ((argb shr 8) and 0xFF) * factor
    val b = (argb and 0xFF) * factor
    return Color(
        red = (r / 255f).coerceIn(0f, 1f),
        green = (g / 255f).coerceIn(0f, 1f),
        blue = (b / 255f).coerceIn(0f, 1f),
        alpha = 1f,
    )
}
