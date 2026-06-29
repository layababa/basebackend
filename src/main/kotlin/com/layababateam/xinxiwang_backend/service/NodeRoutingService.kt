package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.netty.WsResponseHelper
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class NodeRoutingService(
    @Value("\${rentmsg.node.id:node-default}") val nodeId: String,
    private val redisTemplate: StringRedisTemplate,
    private val nodeRoutingPort: NodeRoutingPort,
    private val wsResponseHelper: WsResponseHelper,
) {
    private val log = LoggerFactory.getLogger(NodeRoutingService::class.java)

    @Scheduled(fixedRate = 15_000)
    fun heartbeat() {
        redisTemplate.opsForValue().set(
            "rentmsg:node:$nodeId",
            System.currentTimeMillis().toString(),
            Duration.ofSeconds(30),
        )
        redisTemplate.opsForValue().set(
            "xinxiwang:node:$nodeId",
            System.currentTimeMillis().toString(),
            Duration.ofSeconds(30),
        )
        nodeRoutingPort.refreshClientLogEligibility()
    }

    @RabbitListener(queues = ["rentmsg.route.node.\${rentmsg.node.id:node-default}"])
    fun onCrossNodeMessage(payload: Map<String, Any?>) {
        val action = payload["action"] as? String
        val targetUserId = payload["targetUserId"] as? String ?: return

        when (action) {
            "disconnect_user" -> {
                nodeRoutingPort.disconnectUserLocal(targetUserId)
                log.info("Cross-node disconnect_user for {}", targetUserId)
            }
            "force_disconnect_token" -> {
                val token = payload["token"] as? String ?: return
                val reason = payload["reason"] as? String ?: "Session terminated"
                val channel = nodeRoutingPort.findChannelByToken(targetUserId, token)
                if (channel != null && channel.isActive) {
                    wsResponseHelper.sendRawJson(channel, forceOfflineJson(reason))
                    channel.close()
                }
                log.info("Cross-node force_disconnect_token for {} token={}", targetUserId, token.take(8))
            }
            "client_log_config_updated" -> {
                val message = payload["message"] as? String ?: return
                val delivered = nodeRoutingPort.pushClientLogConfigToLocalEligibleUser(targetUserId, message)
                log.debug("Cross-node client_log_config_updated for {} deliveredDevices={}", targetUserId, delivered)
            }
            else -> {
                val message = payload["message"] as? String ?: return
                val channels = nodeRoutingPort.getChannels(targetUserId)
                if (channels.isEmpty()) {
                    log.debug("Cross-node delivery: user {} not on this node, dropping", targetUserId)
                    return
                }
                channels.forEach { channel ->
                    if (channel.isActive) wsResponseHelper.sendRawJson(channel, message)
                }
                log.debug("Cross-node delivered to {} ({} channels)", targetUserId, channels.size)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        log.info("NodeRoutingService shutting down, removing heartbeat key for node {}", nodeId)
        try {
            redisTemplate.delete("rentmsg:node:$nodeId")
            redisTemplate.delete("xinxiwang:node:$nodeId")
        } catch (e: Throwable) {
            log.warn("Redis cleanup skipped during shutdown: {}", e.javaClass.simpleName)
        }
    }

    private fun forceOfflineJson(reason: String): String {
        return """{"type":"force_offline","message":"${escapeJson(reason)}"}"""
    }

    private fun escapeJson(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}
