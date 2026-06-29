package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
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
class PayNotificationService(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val messageService: MessageService,
    private val objectMapper: ObjectMapper,
    private val userConversationCacheService: UserConversationCacheService
) {
    private val log = LoggerFactory.getLogger(PayNotificationService::class.java)

    companion object {
        const val PAY_USERNAME = "rentmsg_pay"
        const val PAY_DISPLAY_NAME = "RentMsg积分变动通知"
        const val PAY_AVATAR = "https://12da.yufengep.com/logo.png"
        const val CONVERSATION_TYPE_OFFICIAL = 2
        const val CONTENT_TYPE_WALLET_CARD = 12

        const val WELCOME_MESSAGE = "欢迎使用RentMsg积分变动通知"
    }

    @Volatile
    private var payUserId: String? = null

    @PostConstruct
    fun init() {
        val existing = userRepository.findByUsername(PAY_USERNAME)
        if (existing != null) {
            payUserId = existing.id
            if (existing.displayName != PAY_DISPLAY_NAME || existing.avatarUrl != PAY_AVATAR) {
                val updated = existing.copy(
                    displayName = PAY_DISPLAY_NAME,
                    avatarUrl = PAY_AVATAR
                )
                userRepository.save(updated)
                log.info("Pay system user updated: {}", payUserId)
            } else {
                log.info("Pay system user found: {}", payUserId)
            }
        } else {
            val user = userRepository.save(
                User(
                    username = PAY_USERNAME,
                    displayName = PAY_DISPLAY_NAME,
                    avatarUrl = PAY_AVATAR,
                    gender = 2,
                    bio = "RentMsg积分变动提醒官方公众号",
                    passwordHash = UUID.randomUUID().toString(),
                    inviteCode = "",
                    myInviteCode = ""
                )
            )
            payUserId = user.id
            log.info("Pay system user created: {}", payUserId)
        }

        // Send welcome messages to all existing users who don't have a pay conversation yet
        sendWelcomeToAllExistingUsers()
    }

    fun getPayUserId(): String = payUserId
        ?: throw IllegalStateException("Pay user not initialized")

    /**
     * Get or create a type=2 (official) conversation between pay user and target user.
     */
    fun getOrCreatePayConversation(userId: String): String {
        val sysId = getPayUserId()
        val conversations = conversationRepository.findByMembersContainingAndType(sysId, CONVERSATION_TYPE_OFFICIAL)
        val existing = conversations.find { userId in it.members }
        if (existing != null) return existing.id!!

        val now = System.currentTimeMillis()
        val conversation = conversationRepository.save(
            Conversation(
                type = CONVERSATION_TYPE_OFFICIAL,
                members = listOf(sysId, userId),
                createdAt = now
            )
        )

        userConversationRepository.save(
            UserConversation(userId = sysId, conversationId = conversation.id!!, lastReadTime = now, createdAt = now)
        )
        userConversationRepository.save(
            UserConversation(userId = userId, conversationId = conversation.id!!, lastReadTime = 0, createdAt = now)
        )
        userConversationCacheService.invalidate(sysId)
        userConversationCacheService.invalidate(userId)

        log.info("Pay conversation created for user {}, convId={}", userId, conversation.id)
        return conversation.id!!
    }

    /**
     * Create pay conversation and send welcome message for a new user.
     */
    fun createPayConversationWithWelcome(userId: String) {
        try {
            val convId = getOrCreatePayConversation(userId)
            val content = objectMapper.writeValueAsString(mapOf(
                "notifType" to "welcome",
                "title" to "欢迎使用RentMsg积分变动提醒",
                "detail" to "您的RentMsg积分账户已开通，可以使用转账、红包等功能。",
                "time" to System.currentTimeMillis()
            ))
            messageService.sendMessage(
                senderId = getPayUserId(),
                conversationId = convId,
                content = content,
                contentType = CONTENT_TYPE_WALLET_CARD
            )
            log.info("Pay welcome message sent for user {}", userId)
        } catch (e: Exception) {
            log.error("Failed to send pay welcome for user {}: {}", userId, e.message, e)
        }
    }

    /**
     * Send a wallet notification card to the user.
     */
    fun sendPayNotification(
        userId: String,
        notifType: String,
        title: String,
        amount: String? = null,
        detail: String,
        address: String? = null,
        txHash: String? = null
    ) {
        try {
            val convId = getOrCreatePayConversation(userId)
            val payload = mutableMapOf<String, Any?>(
                "notifType" to notifType,
                "title" to title,
                "detail" to detail,
                "time" to System.currentTimeMillis()
            )
            if (amount != null) {
                payload["amount"] = amount
            }
            if (address != null) {
                payload["address"] = address
            }
            if (txHash != null) {
                payload["txHash"] = txHash
            }
            val content = objectMapper.writeValueAsString(payload)
            messageService.sendMessage(
                senderId = getPayUserId(),
                conversationId = convId,
                content = content,
                contentType = CONTENT_TYPE_WALLET_CARD
            )
            log.info("Pay notification [{}] sent for user {}", notifType, userId)
        } catch (e: Exception) {
            log.error("Failed to send pay notification [{}] for user {}: {}", notifType, userId, e.message, e)
        }
    }

    /**
     * Send deposit notification by looking up user from BSC address.
     */
    fun sendDepositNotification(toAddress: String, amount: String, txHash: String?) {
        try {
            val user = userRepository.findByBscAddress(toAddress) ?: return
            sendPayNotification(
                userId = user.id!!,
                notifType = "deposit",
                title = "充值到账通知",
                amount = amount,
                detail = "充值地址: $toAddress\n交易Hash: ${txHash ?: "未知"}",
                address = toAddress,
                txHash = txHash
            )
        } catch (e: Exception) {
            log.error("Failed to send deposit notification for address {}: {}", toAddress, e.message, e)
        }
    }

    /**
     * On startup, ensure all existing users have a pay conversation with welcome message.
     * 優化：先一次查出所有已有官方會話的使用者 ID 集合，再取差集處理，消除 N+1 查詢。
     */
    private fun sendWelcomeToAllExistingUsers() {
        try {
            val sysId = getPayUserId()

            // 一次查出所有已有官方會話的成員 ID 集合
            val existingConvUserIds = conversationRepository
                .findByMembersContainingAndType(sysId, CONVERSATION_TYPE_OFFICIAL)
                .flatMap { it.members }
                .toSet()

            // 分批查使用者，排除系統帳號和已有會話的使用者
            val pageSize = 200
            var page = 0
            var count = 0

            while (true) {
                val pageable = org.springframework.data.domain.PageRequest.of(page, pageSize)
                val userPage = userRepository.findAll(pageable)
                if (userPage.isEmpty) break

                for (user in userPage.content) {
                    if (user.id == sysId) continue
                    if (user.username == SystemUserService.OFFICIAL_USERNAME) continue
                    if (user.id in existingConvUserIds) continue

                    createPayConversationWithWelcome(user.id!!)
                    count++
                }

                if (!userPage.hasNext()) break
                page++
            }

            if (count > 0) {
                log.info("Sent pay welcome messages to {} existing users", count)
            }
        } catch (e: Exception) {
            log.error("Failed to send welcome to existing users: {}", e.message, e)
        }
    }
}
