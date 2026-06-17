package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Report
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ReportRepository : MongoRepository<Report, String> {
    fun findByReporterId(reporterId: String): List<Report>
    fun findByStatus(status: String): List<Report>
    fun findByStatus(status: String, pageable: Pageable): Page<Report>
    fun findByTargetId(targetId: String): List<Report>
    fun findByStatusNotIn(statuses: List<String>, pageable: Pageable): Page<Report>
    fun findByStatusNotIn(statuses: List<String>): List<Report>
    fun findByReporterIdOrderByCreatedAtDesc(reporterId: String, pageable: Pageable): Page<Report>
    fun findByTargetIdOrderByCreatedAtDesc(targetId: String, pageable: Pageable): Page<Report>
}
