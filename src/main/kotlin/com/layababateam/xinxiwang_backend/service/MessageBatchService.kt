package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Batches multiple WS messages destined for the same user within a short window (50ms).
 *
 * When a group chat produces N messages in rapid succession for the same recipient,
 * this service coalesces them into a single `{"type":"batch","messages":[...]}` frame,
 * reducing TCP small-packet overhead and syscall count.
 */
@Service
class MessageBatchService(
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(MessageBatchService::class.java)

    private data class PendingBatch(
        val queue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue(),
        val lock: ReentrantLock = ReentrantLock(),
        @Volatile var scheduledFlush: ScheduledFuture<*>? = null
    )

    private val pendingBatches = ConcurrentHashMap<String, PendingBatch>()
    private val scheduler = Executors.newScheduledThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    ) { r ->
        Thread(r, "msg-batch-${System.nanoTime()}").apply { isDaemon = true }
    }

    companion object {
        private const val BATCH_WINDOW_MS = 50L
        private const val MAX_BATCH_SIZE = 50
    }

    /**
     * Enqueue a message for batched delivery. If only one message arrives within the
     * window it is sent as-is (zero overhead). Multiple messages are wrapped in a
     * batch envelope.
     */
    fun pushBatched(userId: String, message: String, skipApns: Boolean = false) {
        val batch = pendingBatches.computeIfAbsent(userId) { PendingBatch() }
        batch.queue.add(message)

        if (batch.queue.size >= MAX_BATCH_SIZE) {
            batch.scheduledFlush?.cancel(false)
            flush(userId)
            return
        }

        if (batch.scheduledFlush == null || batch.scheduledFlush!!.isDone) {
            batch.scheduledFlush = scheduler.schedule(
                { flush(userId) },
                BATCH_WINDOW_MS,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun flush(userId: String) {
        val batch = pendingBatches[userId] ?: return
        if (!batch.lock.tryLock()) return // 另一个 flush 正在执行，由它处理

        val messages = mutableListOf<String>()
        try {
            while (true) {
                val msg = batch.queue.poll() ?: break
                messages.add(msg)
            }
            // queue 已空且无新消息入队时才移除，避免 computeIfAbsent 与 remove 竞态
            if (batch.queue.isEmpty()) {
                pendingBatches.remove(userId, batch)
            }
        } finally {
            batch.lock.unlock()
        }

        if (messages.isEmpty()) return

        try {
            if (messages.size == 1 || !userSessionManager.userSupportsBatch(userId)) {
                messages.forEach { userSessionManager.pushToUser(userId, it) }
                return
            }

            val parsed = messages.mapNotNull { raw ->
                try {
                    objectMapper.readValue(raw, Map::class.java)
                } catch (_: Exception) {
                    userSessionManager.pushToUser(userId, raw)
                    null
                }
            }

            if (parsed.isEmpty()) return

            val batchPayload = objectMapper.writeValueAsString(
                mapOf("type" to "batch", "messages" to parsed)
            )
            userSessionManager.pushToUser(userId, batchPayload)
            log.debug("Batch-pushed {} messages to user {}", parsed.size, userId)
        } catch (e: Exception) {
            log.error("Failed to flush {} messages for user {}: {}", messages.size, userId, e.message, e)
            // 消息已从 queue 取出但推送失败，不重新入队（由 ACK retry 机制兜底重推）
        }
    }
}
