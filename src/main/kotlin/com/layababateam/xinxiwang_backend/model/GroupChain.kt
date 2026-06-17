package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "group_chains")
data class GroupChain(
    @Id
    val id: String? = null,
    @Indexed
    val conversationId: String,
    @Indexed(unique = true)
    val messageId: String,
    val creatorId: String,
    val title: String,
    val description: String = "",
    val status: Int = 0,             // 0=active, 1=closed
    val maxEntries: Int = 0,         // 0=unlimited
    val allowEdit: Boolean = true,
    val allowRemove: Boolean = true,
    val entries: List<ChainEntry> = emptyList(),
    val version: Long = 0,           // 乐观锁版本号
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
)

data class ChainEntry(
    val userId: String,
    val userName: String,
    val userAvatar: String = "",
    val text: String,
    val seq: Int,                    // 1-based 顺序编号
    val createdAt: Long = System.currentTimeMillis()
)
