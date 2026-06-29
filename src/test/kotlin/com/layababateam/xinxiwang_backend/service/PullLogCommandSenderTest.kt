package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.DebugLogReport
import io.netty.channel.Channel
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import tools.jackson.databind.json.JsonMapper
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PullLogCommandSenderTest {
    private val deliveryPort = object : PullLogDeliveryPort {
        override fun getChannels(userId: String): Set<Channel> = emptySet()
        override fun getTokenForChannel(channel: Channel): String? = null
        override fun sendJsonToChannel(channel: Channel, message: String) = Unit
    }
    private val deviceSessionRepository = unsupportedProxy(
        com.layababateam.xinxiwang_backend.repository.DeviceSessionRepository::class.java,
    )
    private val debugLogReportRepository = unsupportedProxy(
        com.layababateam.xinxiwang_backend.repository.DebugLogReportRepository::class.java,
    )
    private val redisTemplate = RecordingRedisTemplate()
    private val mongoTemplate: MongoTemplate = uninitialized(MongoTemplate::class.java)
    private val objectMapper: JsonMapper = JsonMapper.builder().build()

    private val sender = PullLogCommandSender(
        deliveryPort,
        deviceSessionRepository,
        debugLogReportRepository,
        redisTemplate,
        objectMapper,
        mongoTemplate,
    )

    @Test
    fun `buildCmdPayload contains common pull log protocol fields`() {
        val payload = parsePayload(sender.buildCmdPayload(report()))

        assertEquals("pull_log_cmd", payload["type"])
        assertEquals(1, (payload["v"] as Number).toInt())
        assertEquals("r1", payload["requestId"])
        assertEquals("d1", payload["targetDeviceId"])
        assertEquals(3, (payload["timeRangeDays"] as Number).toInt())
        assertEquals("INFO", payload["logLevel"])
    }

    @Test
    fun `trySend stores offline commands in v2 pending list`() {
        val delivered = sender.trySend(report())

        assertFalse(delivered)
        assertEquals(PullLogCommandSender.pendingKeyV2("u1"), redisTemplate.pushed.single().first)
        assertEquals(Duration.ofDays(7), redisTemplate.expired[PullLogCommandSender.pendingKeyV2("u1")])
        val payload = parsePayload(redisTemplate.pushed.single().second)
        assertEquals("r1", payload["requestId"])
        assertEquals("d1", payload["targetDeviceId"])
    }

    private class RecordingRedisTemplate : StringRedisTemplate() {
        val pushed = mutableListOf<Pair<String, String>>()
        val expired = mutableMapOf<String, Duration>()

        override fun opsForZSet(): ZSetOperations<String, String> = proxy { name, _ ->
            when (name) {
                "add" -> true
                else -> null
            }
        }

        override fun opsForList(): ListOperations<String, String> = proxy { name, args ->
            when (name) {
                "rightPush" -> {
                    pushed += args[0] as String to args[1] as String
                    1L
                }
                "leftPop" -> null
                else -> null
            }
        }

        override fun expire(key: String, timeout: Duration): Boolean {
            expired[key] = timeout
            return true
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> proxy(noinline handler: (String, Array<Any?>) -> Any?): T {
            return Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java),
                InvocationHandler { _, method, args ->
                    when (method.name) {
                        "toString" -> "test-proxy:${T::class.java.simpleName}"
                        "hashCode" -> System.identityHashCode(handler)
                        "equals" -> false
                        else -> handler(method.name, args ?: emptyArray())
                    }
                },
            ) as T
        }

        private fun <T> unsupportedProxy(type: Class<T>): T {
            return Proxy.newProxyInstance(
                type.classLoader,
                arrayOf(type),
                InvocationHandler { _, method, _ ->
                    when (method.name) {
                        "toString" -> "unsupported-proxy:${type.simpleName}"
                        "hashCode" -> 0
                        "equals" -> false
                        else -> error("${type.simpleName}.${method.name} should not be called by this test")
                    }
                },
            ) as T
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> uninitialized(type: Class<T>): T {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null) as sun.misc.Unsafe
            return unsafe.allocateInstance(type) as T
        }
    }

    private fun report(id: String = "r1") = DebugLogReport(
        id = id,
        userId = "u1",
        targetDeviceId = "d1",
        requestedBy = "admin1",
        requestedAt = Instant.now(),
        status = "pending",
        timeRangeDays = 3,
        logLevel = "INFO",
        expireAt = Instant.now().plus(Duration.ofMinutes(5)),
    )

    @Suppress("UNCHECKED_CAST")
    private fun parsePayload(payload: String): Map<String, Any?> =
        objectMapper.readValue(payload, Map::class.java) as Map<String, Any?>
}
