package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.FeedbackDto
import com.layababateam.xinxiwang_backend.dto.SubmitFeedbackRequest
import com.layababateam.xinxiwang_backend.dto.UpdateFeedbackRequest
import com.layababateam.xinxiwang_backend.model.Feedback
import com.layababateam.xinxiwang_backend.repository.FeedbackRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val officialNotificationSender: OfficialNotificationSender
) {
    private val log = LoggerFactory.getLogger(FeedbackService::class.java)

    fun submitFeedback(userId: String, request: SubmitFeedbackRequest): FeedbackDto {
        val feedback = feedbackRepository.save(
            Feedback(
                userId = userId,
                content = request.content,
                images = request.images
            )
        )

        officialNotificationSender.sendOfficialMessage(
            userId = userId,
            content = "感谢您的反馈！\n我们已收到您的意见，将尽快跟进处理，结果将通过此窗口通知您。"
        )

        log.info("Feedback submitted: feedbackId={}, userId={}", feedback.id, userId)
        return toDto(feedback)
    }

    fun updateFeedbackStatus(feedbackId: String, request: UpdateFeedbackRequest): FeedbackDto {
        val feedback = feedbackRepository.findById(feedbackId).orElseThrow {
            IllegalArgumentException("反馈记录不存在")
        }

        val updated = feedbackRepository.save(
            feedback.copy(
                status = request.status,
                adminNote = request.adminNote,
                resolvedAt = System.currentTimeMillis()
            )
        )

        val resultText = if (request.status == "RESOLVED") "已处理" else "不予处理"
        val noteText = if (request.adminNote.isNotBlank()) "\n处理说明：${request.adminNote}" else ""
        officialNotificationSender.sendOfficialMessage(
            userId = feedback.userId,
            content = "您的反馈处理结果：$resultText$noteText\n感谢您帮助我们改善产品体验。"
        )

        log.info("Feedback updated: feedbackId={}, status={}", feedbackId, request.status)
        return toDto(updated)
    }

    fun getFeedbacksByStatus(status: String): List<FeedbackDto> =
        feedbackRepository.findByStatus(status).map { toDto(it) }

    private fun toDto(feedback: Feedback) = FeedbackDto(
        id = feedback.id!!,
        userId = feedback.userId,
        content = feedback.content,
        status = feedback.status,
        adminNote = feedback.adminNote,
        createdAt = feedback.createdAt,
        resolvedAt = feedback.resolvedAt,
        images = feedback.images
    )
}
