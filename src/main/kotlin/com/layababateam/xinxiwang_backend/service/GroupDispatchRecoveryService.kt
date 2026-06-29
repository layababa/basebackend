package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.MessageRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class GroupDispatchRecoveryService(
    private val redisTemplate: StringRedisTemplate,
    private val messageRepository: MessageRepository,
    private val messageBatchService: MessageBatchService,
    private val objectMapper: ObjectMapper,
    @Value("\${rentmsg.node.id:node-default}") private val nodeId: String,
    @Value("\${rentmsg.media.proxy.public-base}") private val proxyPublicBase: String,
) {
    private val log = LoggerFactory.getLogger(GroupDispatchRecoveryService::class.java)

    companion object {
        private const val FAILED_KEY_PREFIX = "rentmsg:group_dispatch_failed:"
        private const val RECOVERY_LOCK_KEY = "rentmsg:lock:group-dispatch-recovery"
        private const val MAX_KEYS_PER_SCAN = 100
        private const val MAX_ENTRIES_PER_KEY = 50
    }

    /**
     * Removes failed-dispatch records for [userId] in [convId] where seqId > [afterSeqId].
     * Called during client sync.
     */
    fun cleanupFailedDispatchForUser(userId: String, convId: String, afterSeqId: Long) {
        val key = "$FAILED_KEY_PREFIX$convId"

        val entries = try {
            redisTemplate.opsForZSet().rangeByScore(
                key,
                afterSeqId.toDouble() + 1,
                Double.MAX_VALUE
            )
        } catch (e: Exception) {
            log.warn("Failed to read dispatch recovery set for conv={}: {}", convId, e.message)
            return
        }

        if (entries.isNullOrEmpty()) return

        val userPrefix = "$userId:"
        val userEntries = entries.filter { it.startsWith(userPrefix) }
        if (userEntries.isEmpty()) return

        try {
            redisTemplate.executePipelined { connection ->
                val keyBytes = key.toByteArray()
                userEntries.forEach { member ->
                    connection.zSetCommands().zRem(keyBytes, member.toByteArray())
                }
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to cleanup dispatch recovery for user={} conv={}: {}", userId, convId, e.message)
        }
    }

    /**
     * 定时扫描失败的群消息分发记录，主动重新投递。
     * 防止在线用户因 push 失败且不触发 sync 而永久丢消息。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    fun recoverFailedDispatches() {
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(RECOVERY_LOCK_KEY, nodeId, Duration.ofSeconds(25))
        if (acquired != true) return

        try {
            doRecovery()
        } catch (e: Exception) {
            log.warn("[DispatchRecovery] scan failed: {}", e.message)
        }
    }

    private fun doRecovery() {
        val scanOptions = ScanOptions.scanOptions()
            .match("$FAILED_KEY_PREFIX*")
            .count(100)
            .build()
        var keysProcessed = 0
        var totalRecovered = 0
        var totalFailed = 0

        redisTemplate.execute { connection ->
            connection.keyCommands().scan(scanOptions).use { cursor ->
                for (rawKey in cursor) {
                    if (keysProcessed >= MAX_KEYS_PER_SCAN) break
                    keysProcessed++

                    val key = String(rawKey)
                    val convId = key.removePrefix(FAILED_KEY_PREFIX)

                    val entries = redisTemplate.opsForZSet().range(key, 0, MAX_ENTRIES_PER_KEY.toLong() - 1)
                    if (entries.isNullOrEmpty()) continue

                    val toRemove = mutableListOf<String>()
                    for (entry in entries) {
                        // entry format: "userId:seqId"
                        val parts = entry.split(":", limit = 2)
                        if (parts.size != 2) {
                            toRemove.add(entry)
                            continue
                        }
                        val userId = parts[0]
                        val seqId = parts[1].toLongOrNull()
                        if (seqId == null) {
                            toRemove.add(entry)
                            continue
                        }

                        try {
                            val message = messageRepository.findByConversationIdAndSeqId(convId, seqId)
                            if (message == null) {
                                // Message not found (not yet persisted or deleted) — skip, will retry next round
                                continue
                            }
                            val normalizedContent = MediaContentUrlNormalizer.normalize(
                                message.content,
                                message.contentType,
                                objectMapper,
                                videoCompatPublicBase = proxyPublicBase,
                            )
                            val payload = objectMapper.writeValueAsString(mapOf(
                                "type" to "new_message",
                                "data" to mapOf(
                                    "id" to message.id,
                                    "conversationId" to message.conversationId,
                                    "senderId" to message.senderId,
                                    "contentType" to message.contentType,
                                    "content" to normalizedContent,
                                    "seqId" to message.seqId,
                                    "createdAt" to message.createdAt,
                                    "mentions" to message.mentions,
                                    "isRecalled" to message.isRecalled
                                )
                            ))
                            messageBatchService.pushBatched(userId, payload)
                            toRemove.add(entry)
                            totalRecovered++
                        } catch (e: Exception) {
                            totalFailed++
                            log.debug("[DispatchRecovery] Failed to redeliver to user={} conv={} seq={}: {}",
                                userId, convId, seqId, e.message)
                        }
                    }

                    // Pipeline remove successfully redelivered entries
                    if (toRemove.isNotEmpty()) {
                        try {
                            redisTemplate.executePipelined { conn ->
                                val keyBytes = key.toByteArray()
                                toRemove.forEach { member ->
                                    conn.zSetCommands().zRem(keyBytes, member.toByteArray())
                                }
                                null
                            }
                        } catch (e: Exception) {
                            log.warn("[DispatchRecovery] Failed to clean entries for conv={}: {}", convId, e.message)
                        }
                    }
                }
            }
            null
        }

        if (totalRecovered > 0 || totalFailed > 0) {
            log.info("[DispatchRecovery] keys={}, recovered={}, failed={}", keysProcessed, totalRecovered, totalFailed)
        }
    }
}
