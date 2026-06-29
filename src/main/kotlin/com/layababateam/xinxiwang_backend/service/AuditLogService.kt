package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.AdminAuditLog
import com.layababateam.xinxiwang_backend.repository.AdminAuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class AuditLogService(
    private val auditLogRepository: AdminAuditLogRepository,
) {
    fun log(
        adminId: String,
        adminUsername: String,
        action: String,
        targetType: String,
        targetId: String? = null,
        details: String? = null,
        ipAddress: String? = null,
    ) {
        val log = AdminAuditLog(
            adminId = adminId,
            adminUsername = adminUsername,
            action = action,
            targetType = targetType,
            targetId = targetId,
            details = details,
            ipAddress = ipAddress,
        )
        auditLogRepository.save(log)
    }

    fun getLogs(pageable: Pageable): Page<AdminAuditLog> =
        auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)

    fun getLogsByAdmin(adminId: String, pageable: Pageable): Page<AdminAuditLog> =
        auditLogRepository.findByAdminId(adminId, pageable)
}
