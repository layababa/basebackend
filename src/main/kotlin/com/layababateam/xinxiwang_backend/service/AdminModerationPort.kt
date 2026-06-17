package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AdminUpdateReportRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface AdminModerationPort {
    fun listReports(status: String?, pageable: Pageable): Page<*>

    fun getReportDetail(reportId: String): Map<String, Any?>?

    fun getReportChatHistory(reportId: String, before: Long?, size: Int): Map<String, Any?>?

    fun getUserReportHistory(userId: String, role: String, pageable: Pageable): Page<*>

    fun updateReport(
        reportId: String,
        body: AdminUpdateReportRequest,
        adminId: String,
        adminUsername: String,
    ): Any?

    fun getUserBanHistory(userId: String): Map<String, Any?>
}
