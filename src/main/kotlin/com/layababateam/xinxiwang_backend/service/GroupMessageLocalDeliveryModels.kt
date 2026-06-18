package com.layababateam.xinxiwang_backend.service

data class GroupMessageLocalDeliveryRequest(
    val userId: String,
    val groupId: String,
    val messagePayload: String,
    val seqId: Long,
    val messageId: String?,
    val traceId: String,
    val contentType: Int,
    val mentions: List<String>,
    val memberCount: Int,
    val onlineCount: Int,
    val skipApnsFanout: Boolean = false,
    val routedFromRemoteNode: Boolean = false,
)

data class GroupMessageLocalDeliveryResult(
    val fullPushed: Boolean,
    val delivered: Boolean = fullPushed,
    val signalDelivered: Boolean = false,
)

object GroupMessageLocalDeliveryPayloads {
    fun parseCrossNodeRequest(
        targetUserId: String,
        payload: Map<String, Any?>,
    ): GroupMessageLocalDeliveryRequest? {
        val groupId = payload["groupId"] as? String ?: return null
        val messagePayload = payload["messagePayload"] as? String ?: return null
        val seqId = (payload["seqId"] as? Number)?.toLong() ?: return null
        val traceId = payload["traceId"] as? String ?: "$groupId:$seqId"
        val mentions = (payload["mentions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return GroupMessageLocalDeliveryRequest(
            userId = targetUserId,
            groupId = groupId,
            messagePayload = messagePayload,
            seqId = seqId,
            messageId = payload["messageId"] as? String,
            traceId = traceId,
            contentType = (payload["contentType"] as? Number)?.toInt() ?: 0,
            mentions = mentions,
            memberCount = (payload["memberCount"] as? Number)?.toInt() ?: 1,
            onlineCount = (payload["onlineCount"] as? Number)?.toInt() ?: 0,
            skipApnsFanout = payload["skipApnsFanout"] as? Boolean ?: false,
            routedFromRemoteNode = true,
        )
    }
}
