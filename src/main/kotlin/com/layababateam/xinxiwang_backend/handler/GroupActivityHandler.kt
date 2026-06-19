package com.layababateam.xinxiwang_backend.handler

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.service.GroupActivityPort
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class GroupActivityResponseSender(
    private val objectMapper: JsonMapper,
) {
    fun send(ctx: ChannelHandlerContext, data: Map<String, Any?>) {
        ctx.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(data)))
    }
}

@Component
class CreateRelayHandler(
    private val groupActivityPort: GroupActivityPort,
    private val responseSender: GroupActivityResponseSender,
) : MessageHandler {
    override val type = "create_relay"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val title = data["title"] as? String ?: throw IllegalArgumentException("接龙标题不能为空")
        val description = data["description"] as? String
        responseSender.send(
            ctx,
            mapOf("type" to "group_relay_created", "data" to groupActivityPort.createRelay(userId, conversationId, title, description)),
        )
    }
}

@Component
class RelayAddEntryHandler(
    private val groupActivityPort: GroupActivityPort,
    private val responseSender: GroupActivityResponseSender,
) : MessageHandler {
    override val type = "relay_add_entry"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val relayId = data["relayId"] as? String ?: throw IllegalArgumentException("relayId不能为空")
        val content = data["content"] as? String ?: throw IllegalArgumentException("接龙内容不能为空")
        responseSender.send(
            ctx,
            mapOf("type" to "relay_add_entry_success", "data" to groupActivityPort.addRelayEntry(userId, relayId, content)),
        )
    }
}

@Component
class RelayCloseHandler(
    private val groupActivityPort: GroupActivityPort,
    private val responseSender: GroupActivityResponseSender,
) : MessageHandler {
    override val type = "relay_close"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val relayId = data["relayId"] as? String ?: throw IllegalArgumentException("relayId不能为空")
        responseSender.send(
            ctx,
            mapOf("type" to "relay_close_success", "data" to groupActivityPort.closeRelay(userId, relayId)),
        )
    }
}

@Component
class CreateCheckinHandler(
    private val groupActivityPort: GroupActivityPort,
    private val responseSender: GroupActivityResponseSender,
) : MessageHandler {
    override val type = "create_checkin"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val title = data["title"] as? String ?: throw IllegalArgumentException("签到标题不能为空")
        val description = data["description"] as? String
        responseSender.send(
            ctx,
            mapOf("type" to "group_checkin_created", "data" to groupActivityPort.createCheckin(userId, conversationId, title, description)),
        )
    }
}

@Component
class CheckinSignHandler(
    private val groupActivityPort: GroupActivityPort,
    private val responseSender: GroupActivityResponseSender,
) : MessageHandler {
    override val type = "checkin_sign"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val checkinId = data["checkinId"] as? String ?: throw IllegalArgumentException("checkinId不能为空")
        val content = data["content"] as? String
        val result = groupActivityPort.signCheckin(userId, checkinId, content)
        responseSender.send(
            ctx,
            mapOf(
                "type" to "checkin_sign_success",
                "data" to result.data,
                "awarded" to result.awarded,
            ),
        )
    }
}

@Component
class CheckinCloseHandler(
    private val groupActivityPort: GroupActivityPort,
    private val responseSender: GroupActivityResponseSender,
) : MessageHandler {
    override val type = "checkin_close"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val checkinId = data["checkinId"] as? String ?: throw IllegalArgumentException("checkinId不能为空")
        responseSender.send(
            ctx,
            mapOf("type" to "checkin_close_success", "data" to groupActivityPort.closeCheckin(userId, checkinId)),
        )
    }
}

private fun conversationId(data: Map<String, Any?>): String =
    data["conversationId"] as? String ?: throw IllegalArgumentException("会话ID不能为空")
