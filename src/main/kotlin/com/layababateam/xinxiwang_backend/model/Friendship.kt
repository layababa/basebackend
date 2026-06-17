package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "friendships")
@CompoundIndex(def = "{'userId': 1, 'friendId': 1}", unique = true)
@CompoundIndex(def = "{'userId': 1, 'version': 1}")
data class Friendship(
    @Id
    val id: String? = null,
    val userId: String,          // 本方 UID
    val friendId: String,        // 对方 UID
    val conversationId: String,  // 共享私聊会话ID
    val remark: String = "",     // 备注名
    val blockedAt: Long? = null,  // 非空时表示已拉黑对方
    val hideMyMoments: Boolean = false,   // 不让他看我的朋友圈
    val hideHisMoments: Boolean = false,  // 不看他的朋友圈
    val chatOnly: Boolean = false,        // 仅聊天（等价于双向隐藏朋友圈）
    val createdAt: Long = System.currentTimeMillis(),
    val version: Long = 0,        // 增量同步版本号，每次变更递增
    val updatedAt: Long = System.currentTimeMillis()
)
