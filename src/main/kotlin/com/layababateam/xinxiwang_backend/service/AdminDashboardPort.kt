package com.layababateam.xinxiwang_backend.service

/**
 * 后台 Dashboard 统计端口。
 *
 * SDK 复用后台统计 HTTP 契约，具体 Mongo 聚合、Redis 缓存和在线指标来源由接入方实现。
 */
interface AdminDashboardPort {
    fun overviewStats(): Any

    fun userGrowthTrend(days: Int): Any

    fun messageVolumeTrend(days: Int): Any

    fun onlineUserCount(): Any

    fun groupGrowthTrend(days: Int): Any

    fun activeUsersTrend(days: Int): Any

    fun topSenders(limit: Int): Any

    fun topGroups(limit: Int): Any

    fun auditLogs(query: AdminAuditLogQuery): Any
}

data class AdminAuditLogQuery(
    val page: Int,
    val size: Int,
    val eventType: String?,
    val adminUsername: String?,
    val targetType: String?,
    val targetId: String?,
    val ip: String?,
    val startAt: Long?,
    val endAt: Long?,
)
