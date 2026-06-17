package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.SessionManagementPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class GetActiveSessionsHandler(
    private val sessionManagementPort: SessionManagementPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "get_active_sessions"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "active_sessions_response",
                "data" to sessionManagementPort.listActiveSessions(userId, ctx.channel()),
            ),
        )
    }
}

@Component
class TerminateSessionHandler(
    private val sessionManagementPort: SessionManagementPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "terminate_session"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val sessionId = data["sessionId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        sessionManagementPort.terminateSession(userId, ctx.channel(), sessionId)
        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "session_terminated",
                "data" to mapOf("sessionId" to sessionId),
            ),
        )
    }
}

@Component
class TerminateAllOtherSessionsHandler(
    private val sessionManagementPort: SessionManagementPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "terminate_all_other_sessions"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val terminated = sessionManagementPort.terminateAllOtherSessions(userId, ctx.channel())
        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "all_other_sessions_terminated",
                "data" to mapOf("count" to terminated),
            ),
        )
    }
}
