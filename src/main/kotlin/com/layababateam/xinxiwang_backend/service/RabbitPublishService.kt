package com.layababateam.xinxiwang_backend.service

import jakarta.annotation.PreDestroy
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class RabbitPublishService(
    private val rabbitTemplate: RabbitTemplate,
) : BroadcastPublisherPort {
    companion object {
        private const val CORE_THREADS = 4
        private const val MAX_THREADS = 4
        private const val QUEUE_CAPACITY = 5_000
    }

    private val log = LoggerFactory.getLogger(RabbitPublishService::class.java)
    private val publishExecutor = ThreadPoolExecutor(
        CORE_THREADS,
        MAX_THREADS,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(QUEUE_CAPACITY),
        { r -> Thread(r, "rabbit-publish-${System.nanoTime()}").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun send(queue: String, payload: Any, action: String) {
        submit(action) {
            rabbitTemplate.convertAndSend(queue, payload)
        }
    }

    fun send(exchange: String, routingKey: String, payload: Any, action: String) {
        submit(action) {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload)
        }
    }

    override fun publishBroadcast(queue: String, payload: Map<String, Any>, action: String) {
        send(queue, payload, action)
    }

    private fun submit(action: String, task: () -> Unit) {
        try {
            publishExecutor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    log.warn(
                        "Rabbit publish degraded: action={} error={}: {}",
                        action,
                        e.javaClass.simpleName,
                        e.message,
                    )
                }
            }
        } catch (_: RejectedExecutionException) {
            log.warn(
                "Rabbit publish dropped: action={} active={} queued={}",
                action,
                publishExecutor.activeCount,
                publishExecutor.queue.size,
            )
        }
    }

    @PreDestroy
    fun shutdown() {
        publishExecutor.shutdown()
        try {
            if (!publishExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                publishExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            publishExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
