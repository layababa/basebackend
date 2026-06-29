package com.layababateam.xinxiwang_backend.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.amqp.rabbit.core.RabbitTemplate

class RabbitPublishServiceTest {
    @Test
    fun `send publishes queue payload asynchronously`() {
        val rabbitTemplate = RecordingRabbitTemplate(expectedMessages = 1)
        val service = RabbitPublishService(rabbitTemplate)

        service.send("queue", mapOf("hello" to "world"), "test queue")

        assertTrue(rabbitTemplate.await())
        assertEquals(listOf(SentRabbitMessage(queue = "queue", payload = mapOf("hello" to "world"))), rabbitTemplate.sent)
        service.shutdown()
    }

    @Test
    fun `publishBroadcast delegates to queue send`() {
        val rabbitTemplate = RecordingRabbitTemplate(expectedMessages = 1)
        val service = RabbitPublishService(rabbitTemplate)

        service.publishBroadcast("broadcast", mapOf("message" to "hello"), "broadcast")

        assertTrue(rabbitTemplate.await())
        assertEquals(
            listOf(SentRabbitMessage(queue = "broadcast", payload = mapOf("message" to "hello"))),
            rabbitTemplate.sent,
        )
        service.shutdown()
    }

    @Test
    fun `send publishes exchange payload asynchronously`() {
        val rabbitTemplate = RecordingRabbitTemplate(expectedMessages = 1)
        val service = RabbitPublishService(rabbitTemplate)

        service.send("exchange", "routing", "payload", "test exchange")

        assertTrue(rabbitTemplate.await())
        assertEquals(
            listOf(SentRabbitMessage(exchange = "exchange", routingKey = "routing", payload = "payload")),
            rabbitTemplate.sent,
        )
        service.shutdown()
    }

    private class RecordingRabbitTemplate(expectedMessages: Int) : RabbitTemplate() {
        private val latch = CountDownLatch(expectedMessages)
        val sent = mutableListOf<SentRabbitMessage>()

        override fun convertAndSend(routingKey: String?, `object`: Any) {
            sent += SentRabbitMessage(queue = routingKey, payload = `object`)
            latch.countDown()
        }

        override fun convertAndSend(exchange: String?, routingKey: String?, `object`: Any) {
            sent += SentRabbitMessage(exchange = exchange, routingKey = routingKey, payload = `object`)
            latch.countDown()
        }

        fun await(): Boolean = latch.await(2, TimeUnit.SECONDS)
    }

    private data class SentRabbitMessage(
        val queue: String? = null,
        val exchange: String? = null,
        val routingKey: String? = null,
        val payload: Any,
    )
}
