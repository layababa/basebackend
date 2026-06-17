package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.PinnedMessage
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

/**
 * 启动时自动将旧的单条置顶消息格式迁移为新的列表格式。
 *
 * 旧格式：pinnedMessageId, pinnedMessageContent, ... 等 8 个扁平字段
 * 新格式：pinnedMessages: List<PinnedMessage>
 *
 * 迁移完成后旧字段会被移除，幂等执行（已迁移的文档不会重复处理）。
 */
@Service
class PinnedMessageMigrationService(
    private val mongoTemplate: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(PinnedMessageMigrationService::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun migratePinnedMessages() {
        try {
            // 查找仍有旧格式置顶字段的文档
            val query = Query(Criteria.where("pinnedMessageId").exists(true).ne(null))
            val docs = mongoTemplate.find(query, Document::class.java, "conversations")

            if (docs.isEmpty()) {
                log.info("[置顶迁移] 无需迁移，未发现旧格式置顶消息")
                return
            }

            var migrated = 0
            for (doc in docs) {
                val id = doc["_id"]?.toString() ?: continue
                val messageId = doc["pinnedMessageId"] as? String ?: continue

                val pinned = PinnedMessage(
                    messageId = messageId,
                    content = doc["pinnedMessageContent"] as? String,
                    contentType = (doc["pinnedMessageContentType"] as? Number)?.toInt() ?: 0,
                    senderId = doc["pinnedMessageSenderId"] as? String,
                    senderName = doc["pinnedMessageSenderName"] as? String,
                    seqId = (doc["pinnedMessageSeqId"] as? Number)?.toLong(),
                    pinnedBy = doc["pinnedBy"] as? String ?: "",
                    pinnedAt = (doc["pinnedAt"] as? Number)?.toLong() ?: 0L
                )

                val pinnedDoc = Document().apply {
                    put("messageId", pinned.messageId)
                    put("content", pinned.content)
                    put("contentType", pinned.contentType)
                    put("senderId", pinned.senderId)
                    put("senderName", pinned.senderName)
                    put("seqId", pinned.seqId)
                    put("pinnedBy", pinned.pinnedBy)
                    put("pinnedAt", pinned.pinnedAt)
                }

                val updateQuery = Query(Criteria.where("_id").`is`(id))
                val update = Update()
                    .set("pinnedMessages", listOf(pinnedDoc))
                    .unset("pinnedMessageId")
                    .unset("pinnedMessageContent")
                    .unset("pinnedMessageContentType")
                    .unset("pinnedMessageSenderId")
                    .unset("pinnedMessageSenderName")
                    .unset("pinnedMessageSeqId")
                    .unset("pinnedBy")
                    .unset("pinnedAt")
                mongoTemplate.updateFirst(updateQuery, update, "conversations")
                migrated++
            }

            log.info("[置顶迁移] 完成：{} 条会话已从旧格式迁移为列表格式", migrated)
        } catch (e: Exception) {
            log.error("[置顶迁移] 迁移失败: {}", e.message, e)
        }
    }
}
