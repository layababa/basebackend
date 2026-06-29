package com.layababateam.xinxiwang_backend.service.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.Friendship
import com.layababateam.xinxiwang_backend.model.SystemConfig
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.FriendshipRepository
import com.layababateam.xinxiwang_backend.repository.SystemConfigRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class RentmsgCacheServicesTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `system config cache reads repository values and parses booleans`() {
        val redisTemplate = RecordingRedisTemplate()
        val repository = systemConfigRepository(
            mapOf("feature.enabled" to SystemConfig(key = "feature.enabled", value = "false")),
        )
        val service = SystemConfigCacheService(redisTemplate, repository)

        assertFalse(service.getBooleanValue("feature.enabled"))
        assertEquals("false", redisTemplate.values["rentmsg:sysconfig:feature.enabled"])
        assertEquals(Duration.ofMinutes(10), redisTemplate.valueTtls["rentmsg:sysconfig:feature.enabled"])
        assertTrue(service.getBooleanValue("missing"))
    }

    @Test
    fun `friendship cache warms friend id set before membership checks`() {
        val redisTemplate = RecordingRedisTemplate()
        val repository = friendshipRepository(
            listOf(Friendship(userId = "u1", friendId = "u2", conversationId = "c1")),
        )
        val service = FriendshipCacheService(redisTemplate, repository)

        assertTrue(service.isFriend("u1", "u2"))
        assertFalse(service.isFriend("u1", "u3"))
        val expectedFriends: Set<String> = setOf("u2")
        assertEquals(expectedFriends, redisTemplate.sets.getValue("rentmsg:friends:u1").toSet())
        val expectedTtl: Duration = Duration.ofMinutes(30)
        assertEquals(expectedTtl, redisTemplate.expirations["rentmsg:friends:u1"])
    }

    @Test
    fun `conversation cache stores conversations and null markers`() {
        val redisTemplate = RecordingRedisTemplate()
        val conversation = Conversation(id = "c1", members = listOf("u1", "u2"))
        val service = ConversationCacheService(
            redisTemplate,
            objectMapper,
            conversationRepository(mapOf("c1" to conversation)),
        )

        assertEquals(conversation, service.getConversation("c1"))
        assertTrue(redisTemplate.values.getValue("rentmsg:conv:c1").contains("\"id\":\"c1\""))
        assertNull(service.getConversation("missing"))
        assertEquals("NULL", redisTemplate.values["rentmsg:conv:missing"])
    }

    @Test
    fun `user cache strips sensitive wallet and payment fields`() {
        val redisTemplate = RecordingRedisTemplate()
        val user = User(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = "avatar.png",
            gender = 0,
            bio = "",
            inviteCode = "invite",
            walletBalance = "99",
            bscAddress = "0xabc",
            paymentPasswordHash = "secret",
        )
        val service = UserCacheService(
            redisTemplate,
            objectMapper,
            userRepository(mapOf("u1" to user)),
            uninitialized(MongoTemplate::class.java),
        )

        val sanitized = service.getUser("u1")

        assertEquals("***", sanitized?.walletBalance)
        assertNull(sanitized?.bscAddress)
        assertNull(sanitized?.paymentPasswordHash)
        val cachedUser = objectMapper.readValue<User>(redisTemplate.values.getValue("rentmsg:user:u1"))
        assertEquals("***", cachedUser.walletBalance)
        assertNull(cachedUser.bscAddress)
    }

    private class RecordingRedisTemplate : StringRedisTemplate() {
        val values = linkedMapOf<String, String>()
        val valueTtls = linkedMapOf<String, Duration>()
        val sets = linkedMapOf<String, MutableSet<String>>()
        val expirations = linkedMapOf<String, Duration>()

        @Suppress("UNCHECKED_CAST")
        private val valueOperations = Proxy.newProxyInstance(
            ValueOperations::class.java.classLoader,
            arrayOf(ValueOperations::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "get" -> values[args?.get(0) as String]
                    "set" -> {
                        val key = args?.get(0) as String
                        values[key] = args[1] as String
                        val ttl = args.getOrNull(2)
                        if (ttl is Duration) valueTtls[key] = ttl
                        null
                    }
                    "toString" -> "RecordingValueOperations"
                    else -> error("Unsupported ValueOperations method: ${method.name}")
                }
            },
        ) as ValueOperations<String, String>

        @Suppress("UNCHECKED_CAST")
        private val setOperations = Proxy.newProxyInstance(
            SetOperations::class.java.classLoader,
            arrayOf(SetOperations::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "members" -> sets[args?.get(0) as String]
                    "isMember" -> sets[args?.get(0) as String]?.contains(args[1] as String)
                    "add" -> {
                        val key = args?.get(0) as String
                        val members = args.drop(1).flatMap { value ->
                            when (value) {
                                is Array<*> -> value.filterIsInstance<String>()
                                is String -> listOf(value)
                                else -> emptyList()
                            }
                        }
                        sets.getOrPut(key) { linkedSetOf() }.addAll(members)
                        members.size.toLong()
                    }
                    "toString" -> "RecordingSetOperations"
                    else -> error("Unsupported SetOperations method: ${method.name}")
                }
            },
        ) as SetOperations<String, String>

        override fun opsForValue(): ValueOperations<String, String> = valueOperations

        override fun opsForSet(): SetOperations<String, String> = setOperations

        override fun hasKey(key: String): Boolean = values.containsKey(key) || sets.containsKey(key)

        override fun expire(key: String, timeout: Duration): Boolean {
            expirations[key] = timeout
            return true
        }

        override fun delete(key: String): Boolean {
            val removedValue = values.remove(key) != null
            val removedSet = sets.remove(key) != null
            return removedValue || removedSet
        }
    }

    private fun conversationRepository(conversations: Map<String, Conversation>): ConversationRepository =
        repositoryProxy(ConversationRepository::class.java) { method, args ->
            when (method) {
                "findById" -> Optional.ofNullable(conversations[args[0] as String])
                "findAllById" -> (args[0] as Iterable<*>).mapNotNull { conversations[it as String] }
                else -> unsupportedRepositoryMethod("ConversationRepository", method)
            }
        }

    private fun friendshipRepository(friendships: List<Friendship>): FriendshipRepository =
        repositoryProxy(FriendshipRepository::class.java) { method, args ->
            when (method) {
                "findByUserId" -> friendships.filter { it.userId == args[0] as String }
                else -> unsupportedRepositoryMethod("FriendshipRepository", method)
            }
        }

    private fun systemConfigRepository(configs: Map<String, SystemConfig>): SystemConfigRepository =
        repositoryProxy(SystemConfigRepository::class.java) { method, args ->
            when (method) {
                "findByKey" -> configs[args[0] as String]
                else -> unsupportedRepositoryMethod("SystemConfigRepository", method)
            }
        }

    private fun userRepository(users: Map<String, User>): UserRepository =
        repositoryProxy(UserRepository::class.java) { method, args ->
            when (method) {
                "findById" -> Optional.ofNullable(users[args[0] as String])
                "findAllById" -> (args[0] as Iterable<*>).mapNotNull { users[it as String] }
                else -> unsupportedRepositoryMethod("UserRepository", method)
            }
        }

    private fun <T> repositoryProxy(type: Class<T>, handler: (String, Array<Any?>) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(
                type.classLoader,
                arrayOf(type),
                InvocationHandler { _, method, args ->
                    when (method.name) {
                        "toString" -> "repository-proxy:${type.simpleName}"
                        "hashCode" -> System.identityHashCode(handler)
                        "equals" -> false
                        else -> handler(method.name, args ?: emptyArray())
                    }
                },
            ),
        )

    private fun unsupportedRepositoryMethod(type: String, method: String): Nothing =
        error("$type.$method should not be called by this test")

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(type: Class<T>): T {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(type) as T
    }
}
