package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable

/** 申请 NOS 上传凭证（头像上传第一步） */
@Serializable
data class NosTokenAllocResp(
    val code: Int? = null,
    val message: String? = null,
    val result: NosToken? = null,
)

@Serializable
data class NosToken(
    val objectKey: String? = null,
    val token: String? = null,
    val docId: String? = null,
)

/** 更新头像的返回：成功时会直接带回新头像 URL */
@Serializable
data class AvatarUploadResp(
    val code: Int? = null,
    val message: String? = null,
    val msg: String? = null,
    val url: String? = null,
)
