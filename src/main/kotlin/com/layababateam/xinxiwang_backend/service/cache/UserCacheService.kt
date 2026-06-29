package com.layababateam.xinxiwang_backend.service.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.UserRepository
import java.time.Duration
import kotlin.random.Random
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Service

@Service
class UserCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    @Suppress("unused") private val mongoTemplate: MongoTemplate,
) {
    private val log = LoggerFactory.getLogger(UserCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "rentmsg:user:"
        private const val NULL_MARKER = "NULL"
        private val TTL = Duration.ofMinutes(30)
        private val NULL_TTL = Duration.ofMinutes(5)
    }

    private fun randomizedTtl(): Duration =
        TTL.plusSeconds(Random.nextLong(0, 300))

    fun getUser(userId: String): User? {
        val key = "$KEY_PREFIX$userId"
        val cached = redisTemplate.opsForValue().get(key)
        if (cached == NULL_MARKER) return null
        if (cached != null) {
            return try {
                objectMapper.readValue<User>(cached)
            } catch (e: Exception) {
                log.warn("Failed to deserialize cached user {}, fallback to DB", userId, e)
                redisTemplate.delete(key)
                null
            }
        }
        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL)
            return null
        }
        val sanitized = sanitizeUser(user)
        cacheUser(sanitized)
        return sanitized
    }

    fun getUsers(userIds: List<String>): Map<String, User> {
        if (userIds.isEmpty()) return emptyMap()

        val distinctIds = userIds.distinct()
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
            log.warn("Pipeline GET for users failed, falling back to individual GET: {}", e.message)
            keys.map { key ->
                try {
                    redisTemplate.opsForValue().get(key)
                } catch (_: Exception) {
                    null
                }
            }
        }

        val result = mutableMapOf<String, User>()
        val missedIds = mutableListOf<String>()

        cachedValues.forEachIndexed { index, json ->
            val uid = distinctIds[index]
            if (json == NULL_MARKER) {
                return@forEachIndexed
            } else if (json != null) {
                try {
                    result[uid] = objectMapper.readValue<User>(json)
                } catch (e: Exception) {
                    log.warn("Failed to deserialize cached user {}", uid, e)
                    redisTemplate.delete("$KEY_PREFIX$uid")
                    missedIds.add(uid)
                }
            } else {
                missedIds.add(uid)
            }
        }

        if (missedIds.isNotEmpty()) {
            val dbUsers = userRepository.findAllById(missedIds)
            val foundIds = mutableSetOf<String>()
            val usersToCache = mutableListOf<User>()
            for (user in dbUsers) {
                val uid = user.id ?: continue
                val sanitized = sanitizeUser(user)
                result[uid] = sanitized
                usersToCache.add(sanitized)
                foundIds.add(uid)
            }
            cacheUsers(usersToCache)
            cacheNullUsers(missedIds.filter { it !in foundIds })
        }

        return result
    }

    fun invalidate(userId: String) {
        redisTemplate.delete("$KEY_PREFIX$userId")
    }

    private fun sanitizeUser(user: User): User = user.copy(
        paymentPasswordHash = null,
        walletBalance = "***",
        bscAddress = null,
    )

    private fun cacheUser(user: User) {
        val uid = user.id ?: return
        try {
            val json = objectMapper.writeValueAsString(user)
            redisTemplate.opsForValue().set("$KEY_PREFIX$uid", json, randomizedTtl())
        } catch (e: Exception) {
            log.warn("Failed to cache user {}", uid, e)
        }
    }

    private fun cacheUsers(users: Collection<User>) {
        if (users.isEmpty()) return
        val entries = users.mapNotNull { user ->
            val uid = user.id ?: return@mapNotNull null
            try {
                uid to objectMapper.writeValueAsString(user)
            } catch (e: Exception) {
                log.warn("Failed to serialize cached user {}", uid, e)
                null
            }
        }
        if (entries.isEmpty()) return
        try {
            @Suppress("UNCHECKED_CAST")
            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
            @Suppress("UNCHECKED_CAST")
            val valueSerializer = redisTemplate.valueSerializer as RedisSerializer<String>
            redisTemplate.executePipelined { connection ->
                entries.forEach { (uid, json) ->
                    connection.stringCommands().setEx(
                        keySerializer.serialize("$KEY_PREFIX$uid")!!,
                        randomizedTtl().seconds,
                        valueSerializer.serialize(json)!!,
                    )
                }
                null
            }
        } catch (e: Exception) {
            log.warn("Pipeline SET for users failed, falling back to individual SET: {}", e.message)
            entries.forEach { (uid, json) ->
                try {
                    redisTemplate.opsForValue().set("$KEY_PREFIX$uid", json, randomizedTtl())
                } catch (ex: Exception) {
                    log.warn("Failed to cache user {}", uid, ex)
                }
            }
        }
    }

    private fun cacheNullUsers(userIds: Collection<String>) {
        val distinctIds = userIds.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) return
        try {
            @Suppress("UNCHECKED_CAST")
            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
            @Suppress("UNCHECKED_CAST")
            val valueSerializer = redisTemplate.valueSerializer as RedisSerializer<String>
            redisTemplate.executePipelined { connection ->
                distinctIds.forEach { uid ->
                    connection.stringCommands().setEx(
                        keySerializer.serialize("$KEY_PREFIX$uid")!!,
                        NULL_TTL.seconds,
                        valueSerializer.serialize(NULL_MARKER)!!,
                    )
                }
                null
            }
        } catch (e: Exception) {
            log.warn("Pipeline SET for null users failed, falling back to individual SET: {}", e.message)
            distinctIds.forEach { uid ->
                try {
                    redisTemplate.opsForValue().set("$KEY_PREFIX$uid", NULL_MARKER, NULL_TTL)
                } catch (ex: Exception) {
                    log.warn("Failed to cache null user {}", uid, ex)
                }
            }
        }
    }
}
