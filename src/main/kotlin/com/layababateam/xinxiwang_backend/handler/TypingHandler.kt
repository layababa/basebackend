package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.model.ConversationType
import com.layababateam.xinxiwang_backend.service.TypingStatusPort
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TypingHandler(
    private val typingStatusPort: TypingStatusPort,
) : MessageHandler {
    private val log = LoggerFactory.getLogger(TypingHandler::class.java)

    override val type: String = "typing_status"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String ?: return
        val memberIds = (data["memberIds"] as? List<*>)?.mapNotNull { it as? String }
        val conversationType = (data["conversationType"] as? Number)?.toInt()

        val targets = if (!memberIds.isNullOrEmpty()) {
            targetsFromClientMembers(userId, conversationId, memberIds, conversationType) ?: return
        } else {
            typingStatusPort.resolveTypingTargets(userId, conversationId)
        }

        if (targets.isNotEmpty()) {
            typingStatusPort.sendTypingStatus(userId, conversationId, targets)
        }
    }

    private fun targetsFromClientMembers(
        userId: String,
        conversationId: String,
        memberIds: List<String>,
        conversationType: Int?,
    ): List<String>? {
        if (memberIds.size > MAX_CLIENT_MEMBER_IDS) {
            log.warn(
                "Dropping typing_status with oversized memberIds (size={}, conversationId={}, userId={})",
                memberIds.size,
                conversationId,
                userId,
            )
            return null
        }

        val isPrivate = when (conversationType) {
            ConversationType.PRIVATE.value,
            ConversationType.SPECIAL_PRIVATE.value -> true
            ConversationType.GROUP.value -> false
            else -> memberIds.size == PRIVATE_MEMBER_COUNT
        }

        return if (isPrivate) {
            listOfNotNull(memberIds.firstOrNull { it != userId })
        } else {
            memberIds.filter { it != userId }
        }
    }

    private companion object {
        const val MAX_CLIENT_MEMBER_IDS = 1000
        const val PRIVATE_MEMBER_COUNT = 2
    }
}
