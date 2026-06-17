package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.DebugLogReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface DebugLogReportRepository : MongoRepository<DebugLogReport, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<DebugLogReport>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<DebugLogReport>
    fun findByStatusInAndExpireAtLessThan(statuses: Collection<String>, now: Instant): List<DebugLogReport>
}
