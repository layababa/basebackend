package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AddCommentRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.MomentDto
import com.layababateam.xinxiwang_backend.dto.PublishMomentRequest
import com.layababateam.xinxiwang_backend.dto.RelationSettingDto
import com.layababateam.xinxiwang_backend.dto.TimelineResponse
import com.layababateam.xinxiwang_backend.dto.UpdateGlobalPrivacyRequest
import com.layababateam.xinxiwang_backend.dto.UpdateRelationSettingRequest

/**
 * 朋友圈 HTTP 能力契约。
 *
 * SDK 复用路由、参数和响应格式；动态存储、好友可见性、未读计数和通知策略由接入方实现。
 */
interface MomentPort {
    fun publishMoment(userId: String, request: PublishMomentRequest): ApiResponse<*>

    fun deleteMoment(userId: String, momentId: String): ApiResponse<*>

    fun likeMoment(userId: String, momentId: String): ApiResponse<*>

    fun unlikeMoment(userId: String, momentId: String): ApiResponse<*>

    fun addComment(userId: String, request: AddCommentRequest): ApiResponse<*>

    fun deleteComment(userId: String, commentId: String): ApiResponse<*>

    fun getUnreadCount(userId: String): Int

    fun clearUnreadCount(userId: String)

    fun getLatestMomentTimestamp(userId: String): Long

    fun getTimeline(userId: String, page: Int, size: Int): TimelineResponse

    fun getUserMoments(userId: String, targetUserId: String, page: Int, size: Int): List<MomentDto>

    fun updateGlobalPrivacy(userId: String, request: UpdateGlobalPrivacyRequest): ApiResponse<*>

    fun getRelationSetting(userId: String, targetUserId: String): RelationSettingDto

    fun updateRelationSetting(userId: String, request: UpdateRelationSettingRequest): ApiResponse<*>
}
