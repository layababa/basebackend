package com.layababateam.xinxiwang_backend.dto

data class ApnsTokenRequest(
    val token: String,
    val voipToken: String? = null,
    val deviceId: String? = null,
    val iosVersion: String? = null,
    // 客户端显式传 null 时按关闭 LiveComm 处理，避免旧 VoIP token 残留。
    val supportsLiveComm: Boolean? = false,
    val notificationAuthorized: Boolean? = true,
)

data class PushDaActiveRequest(
    val bindingUid: String,
)

data class PushDaBindingStatusDto(
    val bound: Boolean,
    val count: Int,
)
