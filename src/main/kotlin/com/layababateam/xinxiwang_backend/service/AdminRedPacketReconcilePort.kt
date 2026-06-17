package com.layababateam.xinxiwang_backend.service

/**
 * 后台红包对账端口。
 *
 * SDK 复用后台 HTTP 契约和汇总结构，Redis/Mongo 对账基线与修复策略由接入方实现。
 */
interface AdminRedPacketReconcilePort {
    fun reconcile(redPacketId: String, dryRun: Boolean): RedPacketReconcileReport

    fun scan(limit: Int, dryRun: Boolean): List<RedPacketReconcileReport>
}

data class RedPacketReconcileReport(
    val redPacketId: String,
    val skipped: Boolean = false,
    val skipReason: String? = null,
    val mongoRemainingCount: Int = 0,
    val redisRemainingCount: Int? = null,
    val mongoRemainingAmount: String = "",
    val redisRemainingAmount: String? = null,
    val mongoClaimedSize: Int = 0,
    val redisClaimedSize: Int? = null,
    val missingInMongo: List<String> = emptyList(),
    val extraInMongo: List<String> = emptyList(),
    val applied: Boolean = false,
    val placeholdersAdded: Int = 0,
)
