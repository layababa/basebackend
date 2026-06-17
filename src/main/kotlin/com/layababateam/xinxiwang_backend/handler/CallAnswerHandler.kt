package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.CallControlPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class CallAcceptHandler(
    private val callControlPort: CallControlPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "call_accept"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val roomId = (data["roomId"] as? Number)?.toInt()
        val deviceId = data["deviceId"] as? String
        val result = callControlPort.acceptCall(userId, targetUserId, roomId, deviceId)
        if (!result.accepted) {
            wsResponseSender.send(
                ctx,
                mapOf(
                    "type" to "call_accept_rejected",
                    "data" to mapOf(
                        "roomId" to roomId,
                        "reason" to (result.rejectReason ?: "another_device_accepted"),
                    ),
                ),
            )
        }
    }
}

@Component
class CallRejectHandler(
    private val callControlPort: CallControlPort,
) : MessageHandler {
    override val type: String = "call_reject"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val roomId = (data["roomId"] as? Number)?.toInt()
        callControlPort.rejectCall(userId, targetUserId, roomId, ctx.channel())
    }
}

@Component
class CallCancelHandler(
    private val callControlPort: CallControlPort,
) : MessageHandler {
    override val type: String = "call_cancel"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val roomId = (data["roomId"] as? Number)?.toInt()
        callControlPort.cancelCall(userId, targetUserId, roomId, ctx.channel())
    }
}

@Component
class CallHangupHandler(
    private val callControlPort: CallControlPort,
) : MessageHandler {
    override val type: String = "call_hangup"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val roomId = (data["roomId"] as? Number)?.toInt()
        val callType = (data["callType"] as? Number)?.toInt() ?: 0
        callControlPort.hangupCall(userId, targetUserId, roomId, callType, ctx.channel())
    }
}

@Component
class CallBusyHandler(
    private val callControlPort: CallControlPort,
) : MessageHandler {
    override val type: String = "call_busy"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val roomId = (data["roomId"] as? Number)?.toInt()
        callControlPort.markBusy(userId, targetUserId, roomId, ctx.channel())
    }
}
