package com.layababateam.xinxiwang_backend.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis-backed bearer token resolver for REST and WebSocket authentication.
 *
 * Token keys intentionally keep the historical `xinxiwang:*` namespace so moving this
 * implementation to the SDK does not invalidate existing sessions.
 */
@Service
class AuthTokenService(
    private val redisTemplate: StringRedisTemplate,
) : AuthTokenResolver {
    companion object {
        const val TOKEN_TTL_DAYS = 30L
        const val DELETED_USER_MARKER_TTL_DAYS = TOKEN_TTL_DAYS + 1
    }

    /**
     * Resolve a user id from `Authorization`.
     *
     * Accepts both `Bearer <token>` and raw token values. Active tokens receive a sliding
     * TTL refresh unless [refreshTtl] is false.
     */
    override fun resolveUserId(authHeader: String?, refreshTtl: Boolean): String? {
        if (authHeader == null) return null
        val token = if (authHeader.startsWith("Bearer ")) authHeader.substring(7) else authHeader
        val redisKey = tokenKey(token)
        val userId = redisTemplate.opsForValue().get(redisKey) ?: return null
        if (isUserDeleted(userId)) {
            redisTemplate.delete(redisKey)
            return null
        }
        if (refreshTtl) {
            redisTemplate.expire(redisKey, TOKEN_TTL_DAYS, TimeUnit.DAYS)
        }
        return userId
    }

    fun markUserDeleted(userId: String) {
        redisTemplate.opsForValue().set(deletedUserKey(userId), "1", DELETED_USER_MARKER_TTL_DAYS, TimeUnit.DAYS)
    }

    fun isUserDeleted(userId: String): Boolean {
        return redisTemplate.hasKey(deletedUserKey(userId)) == true
    }

    private fun tokenKey(token: String): String = "xinxiwang:tokens:$token"

    private fun deletedUserKey(userId: String): String = "xinxiwang:user:deleted:$userId"
}
