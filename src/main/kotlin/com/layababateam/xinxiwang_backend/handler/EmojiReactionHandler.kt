package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class EmojiReactionHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "emoji_reaction"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId 不能为空")
        val emoji = data["emoji"] as? String
            ?: throw IllegalArgumentException("emoji 不能为空")

        require(emoji in ALLOWED_EMOJIS) { "不支持的表情类型" }
        meetingRealtimePort.sendEmojiReaction(userId, meetingId, emoji)
    }

    private companion object {
        val ALLOWED_EMOJIS = setOf("👍", "❤️", "😂", "👏", "🎉", "🔥")
    }
}
