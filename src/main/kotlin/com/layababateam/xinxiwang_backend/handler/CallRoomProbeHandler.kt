package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.CallRoomProbeSessionPort
import com.layababateam.xinxiwang_backend.service.TrtcRoomUsersPort
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CallRoomProbeHandler(
    private val callRoomProbeSessionPort: CallRoomProbeSessionPort,
    private val trtcRoomUsersPort: TrtcRoomUsersPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    private val log = LoggerFactory.getLogger(CallRoomProbeHandler::class.java)
    override val type = "call_room_probe"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val roomId = (data["roomId"] as? Number)?.toInt()
        if (roomId == null) {
            wsResponseSender.send(ctx, response(null, sessionExists = false, belongsToSession = false))
            return
        }

        val session = callRoomProbeSessionPort.getCallRoomProbeSession(roomId)
        if (session == null) {
            wsResponseSender.send(ctx, response(roomId, sessionExists = false, belongsToSession = false))
            return
        }

        if (userId != session.callerId && userId != session.calleeId) {
            log.warn("[CALL-ROOM-PROBE] userId={} tried to probe roomId={} without membership", userId, roomId)
            wsResponseSender.send(ctx, response(roomId, sessionExists = true, belongsToSession = false))
            return
        }

        val peerId = if (session.callerId == userId) session.calleeId else session.callerId
        val activeUsers = trtcRoomUsersPort.activeRoomUsers(roomId)
        val probeStatus = if (activeUsers == null) "unknown" else "ok"
        val selfInRoom = activeUsers?.contains(userId)
        val peerInRoom = activeUsers?.contains(peerId)

        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "call_room_probe_response",
                "data" to mapOf(
                    "roomId" to roomId,
                    "sessionExists" to true,
                    "belongsToSession" to true,
                    "answered" to session.answered,
                    "peerId" to peerId,
                    "selfInRoom" to selfInRoom,
                    "peerInRoom" to peerInRoom,
                    "probeStatus" to probeStatus,
                ),
            ),
        )
    }

    private fun response(
        roomId: Int?,
        sessionExists: Boolean,
        belongsToSession: Boolean,
    ): Map<String, Any?> = mapOf(
        "type" to "call_room_probe_response",
        "data" to mapOf(
            "roomId" to roomId,
            "sessionExists" to sessionExists,
            "belongsToSession" to belongsToSession,
            "answered" to false,
            "peerId" to null,
            "selfInRoom" to null,
            "peerInRoom" to null,
            "probeStatus" to "unavailable",
        ),
    )
}
