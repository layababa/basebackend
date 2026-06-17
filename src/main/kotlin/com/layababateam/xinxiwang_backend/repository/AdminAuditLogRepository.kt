package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.AdminAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AdminAuditLogRepository : MongoRepository<AdminAuditLog, String> {
    fun findByAdminId(adminId: String, pageable: Pageable): Page<AdminAuditLog>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdminAuditLog>
}
