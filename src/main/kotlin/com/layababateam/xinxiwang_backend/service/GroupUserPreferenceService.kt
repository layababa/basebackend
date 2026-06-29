package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.dto.ConversationDto
import com.layababateam.xinxiwang_backend.dto.PinnedMessageDto
import com.layababateam.xinxiwang_backend.extensions.isPrivateChat
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.UserConversation
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 群組個人偏好設定服務
 *
 * 負責使用者在群聊中的個人偏好設定：群內暱稱、群組備註、儲存至通訊錄。
 */
@Service
class GroupUserPreferenceService(
    private val userConversationRepository: UserConversationRepository,
    private val conversationCacheService: ConversationCacheService,
    private val userCacheService: UserCacheService,
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val userConversationCacheService: UserConversationCacheService,
    @Value("\${rentmsg.media.proxy.public-base}") private val proxyPublicBase: String,
) {
    private val log = LoggerFactory.getLogger(GroupUserPreferenceService::class.java)

    fun updateMyNickname(userId: String, convId: String, nickname: String?) {
        val (conv, uc) = resolveMembership(userId, convId)
        val updatedUc = uc.copy(myNickname = nickname)
        userConversationRepository.save(updatedUc)
        userConversationCacheService.invalidate(userId)

        publishGroupEvent("group_member_info_updated", conv.members, mapOf(
            "conversationId" to convId, "userId" to userId, "myNickname" to nickname
        ))
        pushConversationUpdated(userId, conv, updatedUc)
    }

    fun updateGroupRemark(userId: String, convId: String, remark: String?) {
        val (conv, uc) = resolveMembership(userId, convId)
        val updatedUc = uc.copy(groupRemark = remark)
        userConversationRepository.save(updatedUc)
        userConversationCacheService.invalidate(userId)
        pushConversationUpdated(userId, conv, updatedUc)
    }

    fun saveToContacts(userId: String, convId: String, save: Boolean) {
        val (conv, uc) = resolveMembership(userId, convId)
        val updatedUc = uc.copy(savedToContacts = save)
        userConversationRepository.save(updatedUc)
        userConversationCacheService.invalidate(userId)
        pushConversationUpdated(userId, conv, updatedUc)
    }

    // ── Private helpers ──

    /**
     * 以 Conversation.members 為權威來源校驗群成員身份。
     * 若使用者確屬群成員但缺少 UserConversation 文檔（歷史髒數據／雙寫不一致），
     * 自動補建一條，避免誤報「您不在此群」。
     */
    private fun resolveMembership(userId: String, convId: String): Pair<Conversation, UserConversation> {
        val conv = getConvOrThrow(convId)
        require(userId in conv.members) { "您不在此群" }
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, convId)
            ?: UserConversation(userId = userId, conversationId = convId, createdAt = System.currentTimeMillis())
                .also { log.warn("UserConversation missing for member, auto-creating. userId={} convId={}", userId, convId) }
        return conv to uc
    }

    private fun getConvOrThrow(convId: String): Conversation {
        return conversationCacheService.getConversation(convId)
            ?: throw IllegalArgumentException("会话不存在")
    }

    private fun publishGroupEvent(type: String, memberIds: List<String>, data: Map<String, Any?>) {
        rabbitPublishService.send(
            RabbitMQConfig.EVENT_GROUP_EXCHANGE,
            "",
            mapOf("type" to type, "memberIds" to memberIds, "data" to data),
            "group_preference_event:$type members=${memberIds.size} conv=${data["conversationId"] ?: "-"}",
        )
    }

    private fun pushConversationUpdated(userId: String, conv: Conversation, uc: com.layababateam.xinxiwang_backend.model.UserConversation) {
        try {
            val users = userCacheService.getUsers(conv.members)
            val peerUserId = if (conv.isPrivateChat()) conv.members.find { it != userId } else null
            val peerUser = peerUserId?.let { users[it] }

            val dto = ConversationDto(
                id = conv.id!!,
                type = conv.type,
                peerUserId = peerUserId,
                peerUserName = peerUser?.displayName,
                peerUserAvatar = peerUser?.avatarUrl,
                lastMessageContent = normalizeContent(conv.lastMessageContent, conv.lastMessageContentType),
                lastMessageContentType = conv.lastMessageContentType,
                lastMessageSenderId = conv.lastMessageSenderId,
                lastMessageTime = conv.lastMessageTime ?: conv.createdAt,
                unreadCount = 0,
                readSeqId = uc.readSeqId,
                pinned = uc.pinned,
                muted = uc.muted,
                createdAt = conv.createdAt,
                groupName = conv.name,
                groupAvatar = conv.avatarUrl,
                ownerId = conv.ownerId,
                memberCount = conv.members.size,
                muteAll = conv.muteAll,
                blockLinks = conv.blockLinks,
                announcement = conv.announcement,
                myNickname = uc.myNickname,
                groupRemark = uc.groupRemark,
                savedToContacts = uc.savedToContacts,
                mentionedSeqIds = uc.mentionedSeqIds,
                peerRemark = uc.peerRemark,
                joinMode = conv.joinMode,
                addFriendMode = GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
                searchable = conv.searchable,
                historyVisible = conv.historyVisible,
                adminIds = conv.adminIds,
                pinnedMessages = conv.pinnedMessages.map {
                    PinnedMessageDto(
                        messageId = it.messageId,
                        content = normalizeContent(it.content, it.contentType),
                        contentType = it.contentType,
                        senderName = it.senderName,
                        seqId = it.seqId,
                        pinnedBy = it.pinnedBy,
                        pinnedAt = it.pinnedAt,
                    )
                }
            )
            val payload = objectMapper.writeValueAsString(
                mapOf("type" to "conversation_updated", "data" to dto)
            )
            userSessionManager.pushToUser(userId, payload)
        } catch (e: Exception) {
            log.warn("Failed to push conversation_updated for conv={}", conv.id, e)
        }
    }

    private fun normalizeContent(content: String?, contentType: Int): String? {
        return MediaContentUrlNormalizer.normalizeNullable(
            content,
            contentType,
            objectMapper,
            videoCompatPublicBase = proxyPublicBase,
        )
    }
}
