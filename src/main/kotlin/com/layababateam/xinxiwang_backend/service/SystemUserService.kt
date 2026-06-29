package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.model.UserConversation
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SystemUserService(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val messageService: MessageService,
    private val userConversationCacheService: UserConversationCacheService
) {
    private val log = LoggerFactory.getLogger(SystemUserService::class.java)

    companion object {
        const val OFFICIAL_USERNAME = "rentmsg_official"
        const val OFFICIAL_DISPLAY_NAME = "RentMsg团队"
        const val OFFICIAL_AVATAR = "https://12da.yufengep.com/logo.png"
        const val WELCOME_MESSAGE = "欢迎使用 RentMsg，如果有任何问题欢迎反馈给我们"
        const val CONVERSATION_TYPE_OFFICIAL = 2
    }

    @Volatile
    private var officialUserId: String? = null

    @PostConstruct
    fun init() {
        val existing = userRepository.findByUsername(OFFICIAL_USERNAME)
        if (existing != null) {
            officialUserId = existing.id
            if (existing.displayName != OFFICIAL_DISPLAY_NAME || existing.avatarUrl != OFFICIAL_AVATAR) {
                val updated = existing.copy(
                    displayName = OFFICIAL_DISPLAY_NAME,
                    avatarUrl = OFFICIAL_AVATAR
                )
                userRepository.save(updated)
                log.info("Official system user updated: {}", officialUserId)
            } else {
                log.info("Official system user found: {}", officialUserId)
            }
        } else {
            val user = userRepository.save(
                User(
                    username = OFFICIAL_USERNAME,
                    displayName = OFFICIAL_DISPLAY_NAME,
                    avatarUrl = OFFICIAL_AVATAR,
                    gender = 2,
                    bio = "官方公众号",
                    passwordHash = UUID.randomUUID().toString(),
                    inviteCode = "",
                    myInviteCode = ""
                )
            )
            officialUserId = user.id
            log.info("Official system user created: {}", officialUserId)
        }
    }

    fun getOfficialUserId(): String = officialUserId
        ?: throw IllegalStateException("Official user not initialized")

    fun createOfficialConversation(newUserId: String) {
        try {
            val sysId = getOfficialUserId()
            val now = System.currentTimeMillis()

            val conversation = conversationRepository.save(
                Conversation(
                    type = CONVERSATION_TYPE_OFFICIAL,
                    members = listOf(sysId, newUserId),
                    createdAt = now
                )
            )

            userConversationRepository.save(
                UserConversation(userId = sysId, conversationId = conversation.id!!, lastReadTime = now, createdAt = now)
            )
            userConversationRepository.save(
                UserConversation(userId = newUserId, conversationId = conversation.id!!, lastReadTime = 0, createdAt = now)
            )
            userConversationCacheService.invalidate(sysId)
            userConversationCacheService.invalidate(newUserId)

            messageService.sendMessage(
                senderId = sysId,
                conversationId = conversation.id!!,
                content = WELCOME_MESSAGE,
                contentType = 0
            )

            log.info("Official conversation created for user {}, convId={}", newUserId, conversation.id)
        } catch (e: Exception) {
            log.error("Failed to create official conversation for user {}: {}", newUserId, e.message, e)
        }
    }
}
