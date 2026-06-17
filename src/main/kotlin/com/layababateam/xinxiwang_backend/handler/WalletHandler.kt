package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.WalletRealtimePort
import com.layababateam.xinxiwang_backend.service.WalletRedPacketCommand
import com.layababateam.xinxiwang_backend.service.WalletRealtimeResult
import com.layababateam.xinxiwang_backend.service.WalletTransferCommand
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class WalletTransferHandler(
    private val walletRealtimePort: WalletRealtimePort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "wallet_transfer"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val command = WalletTransferCommand(
            receiverId = data["receiverId"] as? String
                ?: throw IllegalArgumentException("接收方ID不能为空"),
            amount = data["amount"] as? String
                ?: throw IllegalArgumentException("金额不能为空"),
            conversationId = data["conversationId"] as? String
                ?: throw IllegalArgumentException("会话ID不能为空"),
            paymentPassword = data["paymentPassword"] as? String
                ?: throw IllegalArgumentException("支付密码不能为空"),
            remark = (data["remark"] as? String)?.take(TRANSFER_REMARK_LIMIT) ?: "",
        )
        wsResponseSender.send(ctx, walletRealtimePort.transfer(userId, command).toResponse())
    }

    private companion object {
        const val TRANSFER_REMARK_LIMIT = 50
    }
}

@Component
class WalletSendRedPacketHandler(
    private val walletRealtimePort: WalletRealtimePort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "wallet_send_red_packet"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val command = WalletRedPacketCommand(
            conversationId = data["conversationId"] as? String
                ?: throw IllegalArgumentException("会话ID不能为空"),
            totalAmount = data["totalAmount"] as? String
                ?: throw IllegalArgumentException("总金额不能为空"),
            count = (data["count"] as? Number)?.toInt() ?: 1,
            rpType = (data["rpType"] as? Number)?.toInt() ?: 0,
            greeting = (data["greeting"] as? String ?: DEFAULT_GREETING).take(GREETING_LIMIT),
            targetUserId = data["targetUserId"] as? String,
            paymentPassword = data["paymentPassword"] as? String
                ?: throw IllegalArgumentException("支付密码不能为空"),
        )
        wsResponseSender.send(ctx, walletRealtimePort.sendRedPacket(userId, command).toResponse())
    }

    private companion object {
        const val DEFAULT_GREETING = "恭喜发财，大吉大利"
        const val GREETING_LIMIT = 100
    }
}

@Component
class WalletClaimRedPacketHandler(
    private val walletRealtimePort: WalletRealtimePort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "wallet_claim_red_packet"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val redPacketId = data["redPacketId"] as? String
            ?: throw IllegalArgumentException("红包ID不能为空")
        wsResponseSender.send(ctx, walletRealtimePort.claimRedPacket(userId, redPacketId).toResponse())
    }
}

@Component
class WalletGetRedPacketInfoHandler(
    private val walletRealtimePort: WalletRealtimePort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "wallet_get_red_packet_info"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val redPacketId = data["redPacketId"] as? String
            ?: throw IllegalArgumentException("红包ID不能为空")
        wsResponseSender.send(ctx, walletRealtimePort.getRedPacketInfo(userId, redPacketId).toResponse())
    }
}

private fun WalletRealtimeResult.toResponse(): Map<String, Any?> =
    buildMap {
        put("type", type)
        if (success != null) put("success", success)
        if (data != null) put("data", data)
        if (message != null) put("message", message)
    }
