package com.layababateam.xinxiwang_backend.service.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import java.time.Duration
import kotlin.random.Random
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Service

@Service
class ConversationCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val conversationRepository: ConversationRepository,
) {
    private val log = LoggerFactory.getLogger(ConversationCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "rentmsg:conv:"
        private const val NULL_MARKER = "NULL"
        private val TTL = Duration.ofMinutes(30)
        private val NULL_TTL = Duration.ofMinutes(5)
    }

    private fun randomizedTtl(): Duration =
        TTL.plusSeconds(Random.nextLong(0, 300))

    fun getConversation(convId: String): Conversation? {
        val key = "$KEY_PREFIX$convId"
        val cached = redisTemplate.opsForValue().get(key)
        if (cached == NULL_MARKER) return null
        if (cached != null) {
            return try {
                objectMapper.readValue<Conversation>(cached)
            } catch (e: Exception) {
                log.warn("Failed to deserialize cached conversation {}, fallback to DB", convId, e)
                redisTemplate.delete(key)
                null
            }
        }
        val conv = conversationRepository.findById(convId).orElse(null)
        if (conv == null) {
            redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL)
            return null
        }
        cacheConversation(conv)
        return conv
    }

    fun getConversations(convIds: List<String>): Map<String, Conversation> {
        if (convIds.isEmpty()) return emptyMap()

        val distinctIds = convIds.distinct()
        val keys = distinctIds.map { "$KEY_PREFIX$it" }
        val cachedValues = try {
            @Suppress("UNCHECKED_CAST")
            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
            redisTemplate.executePipelined { connection ->
                keys.forEach { key ->
                    connection.stringCommands().get(keySerializer.serialize(key)!!)
                }
                null
            }.map { it as? String }
        } catch (e: Exception) {
            log.warn("Pipeline GET for conversations failed, falling back to individual GET: {}", e.message)
            keys.map { key ->
                try {
                    redisTemplate.opsForValue().get(key)
                } catch (_: Exception) {
                    null
                }
            }
        }

        val result = mutableMapOf<String, Conversation>()
        val missedIds = mutableListOf<String>()

        cachedValues.forEachIndexed { index, json ->
            val cid = distinctIds[index]
            if (json == NULL_MARKER) {
                return@forEachIndexed
            } else if (json != null) {
                try {
                    result[cid] = objectMapper.readValue<Conversation>(json)
                } catch (e: Exception) {
                    log.warn("Failed to deserialize cached conversation {}", cid, e)
                    redisTemplate.delete("$KEY_PREFIX$cid")
                    missedIds.add(cid)
                }
            } else {
                missedIds.add(cid)
            }
        }

        if (missedIds.isNotEmpty()) {
            val dbConvs = conversationRepository.findAllById(missedIds)
            val foundIds = mutableSetOf<String>()
            for (conv in dbConvs) {
                val cid = conv.id ?: continue
                result[cid] = conv
                cacheConversation(conv)
                foundIds.add(cid)
            }
            for (cid in missedIds) {
                if (cid !in foundIds) {
                    redisTemplate.opsForValue().set("$KEY_PREFIX$cid", NULL_MARKER, NULL_TTL)
                }
            }
        }

        return result
    }

    fun invalidate(convId: String) {
        redisTemplate.delete("$KEY_PREFIX$convId")
    }

    fun updateCache(conv: Conversation) {
        cacheConversation(conv)
    }

    private fun cacheConversation(conv: Conversation) {
        val cid = conv.id ?: return
        try {
            val json = objectMapper.writeValueAsString(conv)
            redisTemplate.opsForValue().set("$KEY_PREFIX$cid", json, randomizedTtl())
        } catch (e: Exception) {
            log.warn("Failed to cache conversation {}", cid, e)
        }
    }
}
