package com.layababateam.xinxiwang_backend.service.cache

import com.layababateam.xinxiwang_backend.repository.SystemConfigRepository
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class SystemConfigCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val systemConfigRepository: SystemConfigRepository,
) {
    private val log = LoggerFactory.getLogger(SystemConfigCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "rentmsg:sysconfig:"
        private val TTL = Duration.ofMinutes(10)
    }

    fun getValue(key: String): String? {
        val cacheKey = "$KEY_PREFIX$key"
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) return cached

        val config = systemConfigRepository.findByKey(key) ?: return null
        try {
            redisTemplate.opsForValue().set(cacheKey, config.value, TTL)
        } catch (e: Exception) {
            log.warn("Failed to cache system config {}", key, e)
        }
        return config.value
    }

    fun getBooleanValue(key: String, default: Boolean = true): Boolean {
        val value = getValue(key) ?: return default
        return value.toBooleanStrictOrNull() ?: default
    }

    fun invalidate(key: String) {
        deleteQuietly("$KEY_PREFIX$key")
    }

    fun invalidateAll(keys: List<String>) {
        keys.forEach { deleteQuietly("$KEY_PREFIX$it") }
    }

    private fun deleteQuietly(cacheKey: String) {
        try {
            redisTemplate.delete(cacheKey)
        } catch (e: Exception) {
            log.warn("Failed to invalidate system config cache key {}", cacheKey, e)
        }
    }
}
