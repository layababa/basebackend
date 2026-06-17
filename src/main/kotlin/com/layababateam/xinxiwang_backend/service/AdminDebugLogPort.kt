package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.model.DebugLogReport

/**
 * 后台远程调试日志端口。
 *
 * SDK 复用后台 HTTP 契约、状态校验和请求上下文提取，设备校验、命令下发、OSS 签名与审计落库由接入方实现。
 */
interface AdminDebugLogPort {
    fun requestLog(request: AdminDebugLogRequest, context: AdminDebugLogContext): DebugLogReport

    fun listLogs(userId: String?, status: String?, page: Int, size: Int): PagedData<DebugLogReport>

    fun getLog(id: String): DebugLogReport?

    fun createDownload(id: String, context: AdminDebugLogContext): AdminDebugLogDownload

    fun cancelLog(id: String, context: AdminDebugLogContext): DebugLogReport
}

data class AdminDebugLogRequest(
    val userId: String,
    val targetDeviceId: String,
    val timeRangeDays: Int = 3,
    val logLevel: String = "INFO",
)

data class AdminDebugLogContext(
    val adminId: String,
    val ip: String?,
    val userAgent: String?,
)

data class AdminDebugLogDownload(
    val url: String,
    val expiresInSeconds: Long,
)
