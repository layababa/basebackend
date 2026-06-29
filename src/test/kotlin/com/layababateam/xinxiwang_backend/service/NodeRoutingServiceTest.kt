package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.netty.WsResponseHelper
import io.netty.channel.Channel
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeRoutingServiceTest {
    @Test
    fun `client log config route only delivers to local eligible devices`() {
        val port = RecordingNodeRoutingPort()
        val service = NodeRoutingService(
            nodeId = "node-a",
            redisTemplate = uninitialized(StringRedisTemplate::class.java),
            nodeRoutingPort = port,
            wsResponseHelper = uninitialized(WsResponseHelper::class.java),
        )
        val message = """{"type":"client_log_config_updated","data":{"criticalLogEnabled":false,"revision":2}}"""

        service.onCrossNodeMessage(
            mapOf(
                "action" to "client_log_config_updated",
                "targetUserId" to "u1",
                "message" to message,
            ),
        )

        assertEquals("u1" to message, port.clientLogUpdates.single())
    }

    private class RecordingNodeRoutingPort : NodeRoutingPort {
        val clientLogUpdates = mutableListOf<Pair<String, String>>()

        override fun refreshClientLogEligibility(userId: String?) = Unit

        override fun disconnectUserLocal(userId: String) = Unit

        override fun findChannelByToken(userId: String, token: String): Channel? = null

        override fun pushClientLogConfigToLocalEligibleUser(userId: String, message: String): Int {
            clientLogUpdates += userId to message
            return 1
        }

        override fun getChannels(userId: String): Set<Channel> = emptySet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(type: Class<T>): T {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(type) as T
    }
}
