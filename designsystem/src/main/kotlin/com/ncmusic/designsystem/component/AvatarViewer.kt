package com.ncmusic.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 全局头像查看控制器。
 * 由 AppRoot 提供实例，任意位置点击头像即可全屏查看，无需每页各写一份弹窗状态。
 */
class AvatarViewerController(private val onShow: (String) -> Unit) {
    fun show(url: String) {
        if (url.isNotBlank()) onShow(url)
    }
}

val LocalAvatarViewer = staticCompositionLocalOf<AvatarViewerController?> { null }

/**
 * 圆形头像。
 * 和查看封面一样：点击头像全屏看大图；没有提供查看器时（预览等场景）退化为普通头像。
 */
@Composable
fun AvatarImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val viewer = LocalAvatarViewer.current
    val target = url?.takeIf { it.isNotBlank() }
    val clickModifier = if (target != null && viewer != null) {
        modifier.clip(shape).clickable { viewer.show(target) }
    } else {
        modifier
    }
    CoverImage(url = url, modifier = clickModifier, shape = shape)
}

/** 全屏头像预览：黑底大图（比列表头像清晰很多），点任意位置 / 返回键关闭 */
@Composable
fun AvatarViewerDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val context = LocalContext.current
        val request = remember(url, context) {
            ImageRequest.Builder(context)
                .data(hdAvatar(url))
                .crossfade(220)
                .build()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = Str.res(R.string.view_avatar),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = Str.res(R.string.close),
                    tint = Color.White,
                )
            }
        }
    }
}

/** 取高清头像（网易云图片 CDN 支持 param 缩放） */
private fun hdAvatar(url: String): String =
    url.substringBefore('?').takeIf { it.isNotBlank() }?.let { "$it?param=1080y1080" } ?: url
