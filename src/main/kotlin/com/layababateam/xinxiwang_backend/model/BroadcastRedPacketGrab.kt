package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 宣讲红包抢夺记录。`(redPacketId, userId)` 唯一索引保证幂等。
 */
@Document(collection = "broadcast_red_packet_grabs")
@CompoundIndex(name = "uniq_grab", def = "{'redPacketId': 1, 'userId': 1}", unique = true)
data class BroadcastRedPacketGrab(
    @Id
    val id: String? = null,
    @Indexed
    val redPacketId: String,
    val broadcastId: String,
    val userId: String,
    val nickname: String = "",
    val points: Long,
    val grabbedAt: Long = System.currentTimeMillis()
)
