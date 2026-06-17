package com.layababateam.xinxiwang_backend.dto

data class GroupMessageSignalAdminConfigRequest(
    val enabled: Boolean? = null,
    val groupMemberThreshold: Int? = null,
    val onlineMemberThreshold: Int? = null,
    val minProtocolVersion: Int? = null,
    val syncDefaultLimit: Int? = null,
    val syncMaxLimit: Int? = null,
    val rolloutPercent: Int? = null,
    val localConnectionOnly: Boolean? = null,
    val serverSignalCoalesceMs: Long? = null,
    val forceFullPushMessageTypes: String? = null,
)

data class GroupMessageSignalAdminConfigResponse(
    val enabled: Boolean,
    val groupMemberThreshold: Int,
    val onlineMemberThreshold: Int,
    val minProtocolVersion: Int,
    val syncDefaultLimit: Int,
    val syncMaxLimit: Int,
    val rolloutPercent: Int,
    val localConnectionOnly: Boolean,
    val serverSignalCoalesceMs: Long,
    val forceFullPushMessageTypes: String,
)
