package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ReportDto
import com.layababateam.xinxiwang_backend.dto.SubmitReportRequest
import com.layababateam.xinxiwang_backend.dto.UpdateReportRequest
import com.layababateam.xinxiwang_backend.model.Report
import com.layababateam.xinxiwang_backend.repository.ReportRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val officialNotificationSender: OfficialNotificationSender
) {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    fun submitReport(reporterId: String, request: SubmitReportRequest): ReportDto {
        val report = reportRepository.save(
            Report(
                reporterId = reporterId,
                targetId = request.targetId,
                targetType = request.targetType,
                category = request.category,
                description = request.description
            )
        )

        officialNotificationSender.sendOfficialMessage(
            userId = reporterId,
            content = "您的举报已收到！\n举报类型：${request.category}\n我们将在3个工作日内处理，结果将通过此窗口通知您。感谢您的反馈。"
        )

        log.info("Report submitted: reportId={}, reporterId={}, targetId={}", report.id, reporterId, request.targetId)
        return toDto(report)
    }

    fun updateReportStatus(reportId: String, request: UpdateReportRequest): ReportDto {
        val report = reportRepository.findById(reportId).orElseThrow {
            IllegalArgumentException("举报记录不存在")
        }

        val updated = reportRepository.save(
            report.copy(
                status = request.status,
                adminNote = request.adminNote,
                resolvedAt = System.currentTimeMillis()
            )
        )

        val resultText = if (request.status == "RESOLVED") "已处理" else "不予处理"
        val noteText = if (request.adminNote.isNotBlank()) "\n处理说明：${request.adminNote}" else ""
        officialNotificationSender.sendOfficialMessage(
            userId = report.reporterId,
            content = "您的举报处理结果：$resultText$noteText\n感谢您为维护平台环境作出贡献。"
        )

        log.info("Report updated: reportId={}, status={}", reportId, request.status)
        return toDto(updated)
    }

    fun getReportsByStatus(status: String): List<ReportDto> =
        reportRepository.findByStatus(status).map { toDto(it) }

    private fun toDto(report: Report) = ReportDto(
        id = report.id!!,
        reporterId = report.reporterId,
        targetId = report.targetId,
        targetType = report.targetType,
        category = report.category,
        description = report.description,
        status = report.status,
        adminNote = report.adminNote,
        createdAt = report.createdAt,
        resolvedAt = report.resolvedAt
    )
}
