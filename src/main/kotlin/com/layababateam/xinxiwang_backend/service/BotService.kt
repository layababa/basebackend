package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Bot
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.BotRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BotService(
    private val botRepository: BotRepository,
    private val userRepository: UserRepository,
    private val userCacheService: UserCacheService,
    private val redisTemplate: StringRedisTemplate,
    private val passwordEncoder: BCryptPasswordEncoder
) {
    private val log = LoggerFactory.getLogger(BotService::class.java)

    companion object {
        private const val BOT_KEY_PREFIX = "rentmsg:botkey:"
        private const val USERNAME_SUFFIX = "_bot"
    }

    data class BotCreateResult(val bot: Bot, val apiKey: String)

    fun createBot(
        adminId: String,
        username: String,
        displayName: String,
        avatarUrl: String,
        description: String
    ): BotCreateResult {
        val finalUsername = if (username.endsWith(USERNAME_SUFFIX)) username else "$username$USERNAME_SUFFIX"

        require(finalUsername.matches(Regex("^[a-zA-Z0-9_]+$"))) { "用户名仅允许英文字母、数字与下划线" }
        require(finalUsername.length in 3..30) { "用户名长度必须在 3-30 之间" }
        require(!userRepository.existsByUsername(finalUsername)) { "用户名已被占用" }

        val apiKey = UUID.randomUUID().toString().replace("-", "")
        val apiKeyHash: String = passwordEncoder.encode(apiKey)!!

        val user = userRepository.save(
            User(
                username = finalUsername,
                displayName = displayName,
                avatarUrl = avatarUrl,
                gender = 2,
                bio = description,
                passwordHash = "",
                inviteCode = "",
                myInviteCode = "BOT_${UUID.randomUUID().toString().take(8)}",
                isBot = true
            )
        )

        val bot = botRepository.save(
            Bot(
                userId = user.id!!,
                username = finalUsername,
                displayName = displayName,
                avatarUrl = avatarUrl,
                description = description,
                apiKeyHash = apiKeyHash,
                createdBy = adminId
            )
        )

        redisTemplate.opsForValue().set("$BOT_KEY_PREFIX$apiKey", user.id!!)
        log.info("Bot created: username={}, userId={}, by admin={}", finalUsername, user.id, adminId)

        return BotCreateResult(bot, apiKey)
    }

    fun regenerateApiKey(botId: String): String {
        val bot = botRepository.findById(botId).orElseThrow { IllegalArgumentException("Bot 不存在") }

        clearCachedApiKey(bot)

        val newApiKey = UUID.randomUUID().toString().replace("-", "")
        val newHash: String = passwordEncoder.encode(newApiKey)!!

        botRepository.save(bot.copy(apiKeyHash = newHash, updatedAt = System.currentTimeMillis()))
        redisTemplate.opsForValue().set("$BOT_KEY_PREFIX$newApiKey", bot.userId)

        log.info("Bot API key regenerated: botId={}, userId={}", botId, bot.userId)
        return newApiKey
    }

    fun updateBot(botId: String, displayName: String?, avatarUrl: String?, description: String?): Bot {
        val bot = botRepository.findById(botId).orElseThrow { IllegalArgumentException("Bot 不存在") }
        val updated = bot.copy(
            displayName = displayName ?: bot.displayName,
            avatarUrl = avatarUrl ?: bot.avatarUrl,
            description = description ?: bot.description,
            updatedAt = System.currentTimeMillis()
        )
        val saved = botRepository.save(updated)

        val user = userRepository.findById(bot.userId).orElse(null)
        if (user != null) {
            userRepository.save(
                user.copy(
                    displayName = saved.displayName,
                    avatarUrl = saved.avatarUrl,
                    bio = saved.description,
                    version = user.version + 1,
                    updatedAt = System.currentTimeMillis()
                )
            )
            userCacheService.invalidate(bot.userId)
        }

        return saved
    }

    fun updateBotStatus(botId: String, status: Int): Bot {
        val bot = botRepository.findById(botId).orElseThrow { IllegalArgumentException("Bot 不存在") }
        val updated = bot.copy(status = status, updatedAt = System.currentTimeMillis())
        if (status == 0) clearCachedApiKey(bot)
        return botRepository.save(updated)
    }

    fun deleteBot(botId: String) {
        val bot = botRepository.findById(botId).orElse(null) ?: return
        clearCachedApiKey(bot)
        botRepository.deleteById(botId)
        userRepository.deleteById(bot.userId)
        userCacheService.invalidate(bot.userId)
        log.info("Bot deleted: botId={}, userId={}", botId, bot.userId)
    }

    fun resolveUserIdByApiKey(apiKey: String): String? {
        val cached = redisTemplate.opsForValue().get("$BOT_KEY_PREFIX$apiKey")
        if (cached != null) return cached

        val allBots = botRepository.findAll()
        for (bot in allBots) {
            if (bot.status == 1 && passwordEncoder.matches(apiKey, bot.apiKeyHash)) {
                redisTemplate.opsForValue().set("$BOT_KEY_PREFIX$apiKey", bot.userId)
                return bot.userId
            }
        }
        return null
    }

    fun getBotByUserId(userId: String): Bot? = botRepository.findByUserId(userId)

    private fun clearCachedApiKey(bot: Bot) {
        val pattern = "$BOT_KEY_PREFIX*"
        try {
            val keys = redisTemplate.keys(pattern)
            keys?.forEach { key ->
                val value = redisTemplate.opsForValue().get(key)
                if (value == bot.userId) {
                    redisTemplate.delete(key)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to clear cached bot API keys for userId={}", bot.userId, e)
        }
    }
}
