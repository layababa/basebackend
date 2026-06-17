package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Phase 2 调试日志拉取记录（计划文件 §三 / §G1 §G3 §G4 §G12 §G14）。
 *
 * status 流转：pending → sent → acked → uploaded
 *           或 pending/sent/acked → failed / timeout / cancelled
 */
@Document(collection = "debug_log_reports")
data class DebugLogReport(
    @Id val id: String? = null,
    @Version val version: Long? = null,
    @Indexed val userId: String,
    val targetDeviceId: String,             // §G3 必填，锁定到唯一 device session
    @Indexed val requestedBy: String,       // 触发的 admin id
    val requestedAt: Instant,
    val status: String,                     // pending/sent/acked/uploaded/failed/timeout/cancelled
    val timeRangeDays: Int,
    val logLevel: String,                   // INFO/DEBUG
    val logObjectKey: String? = null,       // 私有 bucket 内 object key，下载需后端签预签名 URL
    val fileSize: Long? = null,
    val fileCount: Int? = null,
    val errorCode: String? = null,
    val errorMsg: String? = null,
    @Indexed val expireAt: Instant,         // §G4 / §G12 5 分钟后过期
    val sentAt: Instant? = null,
    val ackedAt: Instant? = null,
    val uploadedAt: Instant? = null,
    val auditTrail: List<AuditEntry> = emptyList(),  // §G14
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class AuditEntry(
    val action: String,    // request / cancel / download
    val by: String,        // admin id
    val at: Instant,
    val ip: String? = null,
    val userAgent: String? = null,
    val errorMsg: String? = null,
)
