package com.layababateam.xinxiwang_backend.handler

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.PendingCallPort
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CheckPendingCallHandler(
    private val pendingCallPort: PendingCallPort,
    private val objectMapper: JsonMapper,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    private val log = LoggerFactory.getLogger(CheckPendingCallHandler::class.java)

    override val type = "check_pending_call"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        log.info("[CHECK-PENDING-CALL] userId={}", userId)

        val payload = pendingCallPort.peekPendingCall(userId)
        if (payload == null) {
            log.debug("[CHECK-PENDING-CALL] No pending call for userId={}", userId)
            return
        }

        val parsed = parsePayload(payload)
        val roomId = extractRoomId(parsed)
        if (roomId != null && pendingCallPort.hasActiveCallSession(roomId)) {
            wsResponseSender.send(ctx, parsed)
            log.info("[CHECK-PENDING-CALL] Pending call found and forwarded for userId={}, roomId={}", userId, roomId)
            return
        }

        pendingCallPort.clearPendingCall(userId)
        log.info("[CHECK-PENDING-CALL] Pending call found but session expired for userId={}, roomId={}", userId, roomId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePayload(payload: String): Map<String, Any?> =
        objectMapper.readValue(payload, Map::class.java) as Map<String, Any?>

    private fun extractRoomId(payload: Map<String, Any?>): Int? {
        val callData = payload["data"] as? Map<*, *> ?: return null
        return (callData["roomId"] as? Number)?.toInt()
    }
}
