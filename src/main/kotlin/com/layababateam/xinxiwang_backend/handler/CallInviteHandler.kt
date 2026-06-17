package com.layababateam.xinxiwang_backend.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.service.CallInvitePort
import com.layababateam.xinxiwang_backend.service.CallInviteResponseSink
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class CallInviteHandler(
    private val callInvitePort: CallInvitePort,
    private val objectMapper: ObjectMapper,
) : MessageHandler {
    override val type = "call_invite"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val callType = (data["callType"] as? Number)?.toInt() ?: 0

        callInvitePort.handleCallInvite(
            userId = userId,
            targetUserId = targetUserId,
            callType = callType,
            responseSink = CallInviteResponseSink { payload ->
                ctx.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(payload)))
            },
        )
    }
}
