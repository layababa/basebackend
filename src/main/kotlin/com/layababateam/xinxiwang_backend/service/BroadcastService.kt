package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BroadcastService(
    private val publisher: BroadcastPublisherPort,
) {
    private val log = LoggerFactory.getLogger(BroadcastService::class.java)

    fun broadcast(message: String, adminId: String) {
        val broadcastId = UUID.randomUUID().toString()
        log.info("Broadcasting message from admin {}, broadcastId={}: {}", adminId, broadcastId, message.take(50))
        publisher.publishBroadcast(
            RabbitMQConfig.BROADCAST_QUEUE,
            mapOf(
                "message" to message,
                "adminId" to adminId,
                "broadcastId" to broadcastId,
                "timestamp" to System.currentTimeMillis(),
            ),
            "broadcast admin=$adminId broadcastId=$broadcastId",
        )
    }
}
