package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "friend_requests")
@CompoundIndex(def = "{'toUserId': 1, 'status': 1}")
data class FriendRequest(
    @Id
    val id: String? = null,
    @Indexed
    val fromUserId: String,      // 发起方 UID
    val toUserId: String,        // 接收方 UID
    val message: String = "",    // 验证消息
    val status: Int = 0,         // 0=pending, 1=accepted, 2=rejected, 3=permanently_rejected
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
