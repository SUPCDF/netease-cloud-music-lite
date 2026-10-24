package com.ncmusic.designsystem.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌曲网页链接（官方站点，分享出去可直接打开） */
fun songShareUrl(songId: Long): String = "https://music.163.com/song?id=$songId"

/** 分享歌曲：唤起系统分享面板（返回 false 表示没有可用的分享目标） */
fun shareSong(context: Context, songId: Long, title: String, artist: String): Boolean {
    val text = buildString {
        append(title)
        if (artist.isNotBlank()) {
            append(" - ")
            append(artist)
        }
        append('\n')
        append(songShareUrl(songId))
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, Str.res(R.string.share_to)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/** 复制歌曲链接到剪贴板（返回 false 表示剪贴板不可用） */
fun copySongLink(context: Context, songId: Long): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    return runCatching {
        clipboard.setPrimaryClip(ClipData.newPlainText(Str.res(R.string.song_link), songShareUrl(songId)))
    }.isSuccess
}
