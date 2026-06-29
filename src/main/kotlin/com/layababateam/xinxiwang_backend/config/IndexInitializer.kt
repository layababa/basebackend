package com.layababateam.xinxiwang_backend.config

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.stereotype.Component

@Component
class IndexInitializer(
    private val mongoTemplate: MongoTemplate,
) {
    private val log = LoggerFactory.getLogger(IndexInitializer::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Order(0)
    fun initIndexes() {
        try {
            deduplicateUserConversations()
            createIndexesSafe()
            log.info("[IndexInit] All indexes ensured successfully")
        } catch (e: Exception) {
            log.error("[IndexInit] Failed to initialize indexes: {}", e.message, e)
        }
    }

    private fun deduplicateUserConversations() {
        val collection = mongoTemplate.getCollection("user_conversations")
        val pipeline = listOf(
            Document(
                "\$group",
                Document("_id", Document("userId", "\$userId").append("conversationId", "\$conversationId"))
                    .append("ids", Document("\$push", "\$_id"))
                    .append("count", Document("\$sum", 1)),
            ),
            Document("\$match", Document("count", Document("\$gt", 1))),
        )
        val duplicates = collection.aggregate(pipeline).toList()
        if (duplicates.isEmpty()) {
            log.info("[IndexInit] No duplicate user_conversations found")
            return
        }

        var removed = 0
        for (doc in duplicates) {
            val ids = doc.getList("ids", Any::class.java)
            val toDelete = ids.drop(1)
            if (toDelete.isNotEmpty()) {
                collection.deleteMany(Document("_id", Document("\$in", toDelete)))
                removed += toDelete.size
            }
        }
        log.warn("[IndexInit] Removed {} duplicate user_conversations entries", removed)
    }

    private fun createIndexesSafe() {
        ensureIndex(
            "user_conversations",
            CompoundIndexDefinition(Document("userId", 1).append("conversationId", 1)).unique(),
        )
        ensureIndex(
            "user_conversations",
            Index().on("conversationId", Sort.Direction.ASC),
        )

        ensureIndex(
            "messages",
            CompoundIndexDefinition(Document("conversationId", 1).append("seqId", 1)),
        )
        ensureIndex(
            "messages",
            CompoundIndexDefinition(Document("conversationId", 1).append("seqId", -1)).named("idx_conv_seq_desc"),
        )
        ensureIndex(
            "messages",
            CompoundIndexDefinition(
                Document("conversationId", 1).append("senderId", 1).append("createdAt", 1),
            ).named("idx_conv_sender_created"),
        )
        ensureIndex(
            "messages",
            CompoundIndexDefinition(Document("conversationId", 1).append("createdAt", 1)).named("idx_conv_created"),
        )
        ensureIndex(
            "messages",
            CompoundIndexDefinition(
                Document("contentType", 1).append("senderId", 1).append("createdAt", -1),
            ).named("idx_msg_callhistory"),
        )
        ensureIndex(
            "messages",
            CompoundIndexDefinition(Document("contentType", 1).append("createdAt", -1)).named("idx_msg_type_created"),
        )

        ensureIndex(
            "friendships",
            CompoundIndexDefinition(Document("userId", 1).append("friendId", 1)).unique().named("idx_user_friend"),
        )
        ensureIndex(
            "friendships",
            CompoundIndexDefinition(Document("friendId", 1).append("userId", 1)).named("idx_friend_user"),
        )
        ensureIndex(
            "friendships",
            CompoundIndexDefinition(Document("userId", 1).append("version", 1)).named("idx_user_version"),
        )

        ensureIndex(
            "conversations",
            Index().on("members", Sort.Direction.ASC).named("idx_members"),
        )

        ensureIndex(
            "device_sessions",
            Index().on("token", Sort.Direction.ASC).unique().named("idx_token"),
        )
        ensureIndex(
            "device_sessions",
            Index().on("userId", Sort.Direction.ASC).named("idx_device_user"),
        )
    }

    @Suppress("DEPRECATION")
    private fun ensureIndex(collection: String, indexDef: IndexDefinition) {
        try {
            mongoTemplate.indexOps(collection).ensureIndex(indexDef)
        } catch (e: Exception) {
            log.warn("[IndexInit] Failed to create index on {}: {}", collection, e.message)
        }
    }
}
