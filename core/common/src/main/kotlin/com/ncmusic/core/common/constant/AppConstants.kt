package com.ncmusic.core.common.constant

/** 全局常量 */
object AppConstants {

    /**
     * NeteaseCloudMusicApi 默认地址。
     *
     * 注意：10.0.2.2 是 Android 模拟器访问「宿主机 127.0.0.1」的特殊地址，
     * 所以默认值不是 127.0.0.1 而是 10.0.2.2。
     * 真机调试请在「设置 -> 接口地址」里改成电脑的局域网 IP，例如 http://192.168.1.100:3000
     */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"

    /** 默认音质等级：standard / higher / exhigh / lossless（实际返回以账号权限为准） */
    const val DEFAULT_SOUND_QUALITY = "exhigh"

    /** 默认播放倍速 */
    const val DEFAULT_PLAYBACK_SPEED = 1.0f
}
