package com.layababateam.xinxiwang_backend.service.cache

import com.layababateam.xinxiwang_backend.repository.FriendshipRepository
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class FriendshipCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val friendshipRepository: FriendshipRepository,
) {
    private val log = LoggerFactory.getLogger(FriendshipCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "rentmsg:friends:"
        private val TTL = Duration.ofMinutes(30)
    }

    fun getFriendIds(userId: String): Set<String> {
        val key = "$KEY_PREFIX$userId"
        val cached = redisTemplate.opsForSet().members(key)
        if (!cached.isNullOrEmpty()) return cached

        val friendIds = friendshipRepository.findByUserId(userId)
            .map { it.friendId }
            .toSet()
        if (friendIds.isNotEmpty()) {
            cacheFriendIds(userId, friendIds)
        }
        return friendIds
    }

    fun isFriend(userId: String, friendId: String): Boolean {
        val key = "$KEY_PREFIX$userId"
        val exists = redisTemplate.hasKey(key)
        if (exists == true) {
            return redisTemplate.opsForSet().isMember(key, friendId) ?: false
        }
        val friendIds = getFriendIds(userId)
        return friendId in friendIds
    }

    fun invalidate(userId: String) {
        redisTemplate.delete("$KEY_PREFIX$userId")
    }

    private fun cacheFriendIds(userId: String, friendIds: Set<String>) {
        val key = "$KEY_PREFIX$userId"
        try {
            redisTemplate.opsForSet().add(key, *friendIds.toTypedArray())
            redisTemplate.expire(key, TTL)
        } catch (e: Exception) {
            log.warn("Failed to cache friend ids for user {}", userId, e)
        }
    }
}
