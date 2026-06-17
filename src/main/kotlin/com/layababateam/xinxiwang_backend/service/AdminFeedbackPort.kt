package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AdminFeedbackReplyRequest
import com.layababateam.xinxiwang_backend.dto.AdminUpdateFeedbackRequest
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.model.Feedback

/**
 * 后台反馈处理端口。
 *
 * SDK 复用后台 HTTP 契约、参数校验和审计动作，反馈持久化、用户通知与奖励积分由接入方实现。
 */
interface AdminFeedbackPort {
    fun listFeedback(status: String?, page: Int, size: Int): PagedData<Feedback>
    fun findFeedback(id: String): Feedback?
    fun updateFeedback(id: String, body: AdminUpdateFeedbackRequest): Feedback?
    fun replyToFeedback(id: String, adminId: String, adminUsername: String, body: AdminFeedbackReplyRequest): Feedback?
}
