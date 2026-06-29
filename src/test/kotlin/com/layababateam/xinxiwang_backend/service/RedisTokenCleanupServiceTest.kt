package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.data.redis.core.StringRedisTemplate

class RedisTokenCleanupServiceTest {
    @Test
    fun `deleteTokenKeys deletes each key independently`() {
        val redisTemplate = RecordingRedisTemplate(setOf("rentmsg:tokens:a", "rentmsg:tokens:c"))
        val service = RedisTokenCleanupService(redisTemplate)

        val removed = service.deleteTokenKeys(
            listOf("rentmsg:tokens:a", "rentmsg:tokens:b", "rentmsg:tokens:c"),
            "test",
        )

        assertEquals(2, removed)
        assertEquals(
            listOf("rentmsg:tokens:a", "rentmsg:tokens:b", "rentmsg:tokens:c"),
            redisTemplate.deletedKeys,
        )
    }

    @Test
    fun `deleteByTokens prefixes raw token values`() {
        val redisTemplate = RecordingRedisTemplate(setOf("rentmsg:tokens:t1"))
        val service = RedisTokenCleanupService(redisTemplate)

        val removed = service.deleteByTokens(listOf("t1", "t2"), "test")

        assertEquals(1, removed)
        assertEquals(listOf("rentmsg:tokens:t1", "rentmsg:tokens:t2"), redisTemplate.deletedKeys)
    }

    private class RecordingRedisTemplate(
        private val removableKeys: Set<String>,
    ) : StringRedisTemplate() {
        val deletedKeys = mutableListOf<String>()

        override fun delete(key: String): Boolean {
            deletedKeys += key
            return key in removableKeys
        }
    }
}
