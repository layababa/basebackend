package com.layababateam.xinxiwang_backend.dto

data class GroupV3SyncRequest(
    val groupId: String = "",
    val fromSeqId: Long = 0,
    val limit: Int? = null,
    val deviceId: String? = null,
    val traceId: String? = null,
)

data class GroupV3SyncResponse(
    val groupId: String,
    val fromSeqId: Long,
    val nextCursorSeqId: Long,
    val serverMaxSeqId: Long,
    val hasMore: Boolean,
    val messages: List<MessageDto>,
)
