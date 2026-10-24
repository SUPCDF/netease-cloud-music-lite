package com.ncmusic.feature.mine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/**
 * 头像图片选择器。
 *
 * 优先使用系统照片选择器（Android 13+ 的网格界面）；旧系统使用相册的
 * ACTION_PICK 网格界面，而不是会打开文件管理器的 ACTION_OPEN_DOCUMENT。
 * 只有设备上完全没有相册应用时才回退到系统文件选择器，避免直接崩溃。
 */
class GridImagePickerContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val photoPicker = Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
            }
            if (photoPicker.resolveActivity(context.packageManager) != null) {
                return photoPicker
            }
        }

        val gallery = Intent(Intent.ACTION_PICK).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        }
        if (gallery.resolveActivity(context.packageManager) != null) {
            return gallery
        }

        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
