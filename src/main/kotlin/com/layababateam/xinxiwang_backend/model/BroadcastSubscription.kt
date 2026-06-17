package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 宣讲大会推送订阅记录。
 *
 * 触发逻辑：用户进入 BroadcastDetailPage = 自动 upsert 一条订阅。订阅后系统在
 * 宣讲开始前 15 分钟、正式开始时、被取消时各推一条通知。
 *
 * 已发字段（notifiedReminder/Start）由调度器在 dispatch 后置 true，避免重复推送。
 * 取消通知在 cancel 接口同步路径里发，不需要 dispatch 标记。
 *
 * 联合唯一索引 (broadcastId, userId)：每对只保留一条订阅，重复进详情页时
 * upsert 会更新 subscribedAt 但不会重置 notifiedReminder/Start——避免用户
 * 多次进出导致同一条通知反复推。
 */
@Document(collection = "broadcast_subscriptions")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_broadcast_user",
        def = "{'broadcastId': 1, 'userId': 1}",
        unique = true,
    ),
)
data class BroadcastSubscription(
    @Id
    val id: String? = null,
    @Indexed
    val broadcastId: String,
    @Indexed
    val userId: String,
    val subscribedAt: Long = System.currentTimeMillis(),
    /** 15 分钟提醒已发；调度器扫描时跳过已发的。 */
    val notifiedReminder: Boolean = false,
    /** 开始通知已发；状态从 scheduled/waiting → live 触发时置 true。 */
    val notifiedStart: Boolean = false,
)
