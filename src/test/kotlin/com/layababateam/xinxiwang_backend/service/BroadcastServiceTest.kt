package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BroadcastServiceTest {
    @Test
    fun `broadcast publishes queue payload with admin and generated metadata`() {
        val publisher = RecordingBroadcastPublisher()
        val service = BroadcastService(publisher)

        service.broadcast("hello", "admin-1")

        val sent = publisher.sent.single()
        assertEquals(RabbitMQConfig.BROADCAST_QUEUE, sent.queue)
        assertEquals("broadcast admin=admin-1 broadcastId=${sent.payload["broadcastId"]}", sent.action)
        assertEquals("hello", sent.payload["message"])
        assertEquals("admin-1", sent.payload["adminId"])
        assertTrue((sent.payload["broadcastId"] as String).isNotBlank())
        assertNotNull(sent.payload["timestamp"] as Long)
    }

    private class RecordingBroadcastPublisher : BroadcastPublisherPort {
        val sent = mutableListOf<SentBroadcast>()

        override fun publishBroadcast(queue: String, payload: Map<String, Any>, action: String) {
            sent += SentBroadcast(queue, payload, action)
        }
    }

    private data class SentBroadcast(
        val queue: String,
        val payload: Map<String, Any>,
        val action: String,
    )
}
