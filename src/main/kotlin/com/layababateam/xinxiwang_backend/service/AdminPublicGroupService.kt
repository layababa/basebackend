package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.PublicGroupApply
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.PublicGroupApplyRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class AdminPublicGroupService(
    private val publicGroupApplyRepository: PublicGroupApplyRepository,
    private val conversationRepository: ConversationRepository,
    private val auditLogService: AuditLogService,
    private val mongoTemplate: MongoTemplate,
    private val conversationCacheService: ConversationCacheService
) {

    /**
     * 用 $set 部分更新 publicState，避免全量 save(conv.copy(publicState=X))
     * 把 addFriendMode 等并发写入的字段刷回默认值。
     */
    private fun updatePublicState(groupId: String, publicState: Int) {
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(groupId)),
            Update().set("publicState", publicState),
            Conversation::class.java
        )
        conversationCacheService.invalidate(groupId)
    }

    fun listApplies(status: Int?, pageable: Pageable): Page<PublicGroupApply> {
        return if (status != null) {
            publicGroupApplyRepository.findByStatus(status, pageable)
        } else {
            publicGroupApplyRepository.findAllByOrderByCreatedAtDesc(pageable)
        }
    }

    fun acceptApply(
        applyId: String,
        adminId: String,
        adminUsername: String
    ): Result<PublicGroupApply> {
        val apply = publicGroupApplyRepository.findById(applyId).orElse(null)
            ?: return Result.failure(IllegalArgumentException("申请记录不存在"))

        if (apply.status != 0) {
            return Result.failure(IllegalStateException("该申请已被处理"))
        }

        val conversation = conversationRepository.findById(apply.groupId).orElse(null)
            ?: return Result.failure(IllegalArgumentException("群组不存在"))

        val now = System.currentTimeMillis()
        val updatedApply = publicGroupApplyRepository.save(
            apply.copy(status = 1, reviewedBy = adminId, reviewedAt = now)
        )

        updatePublicState(apply.groupId, 2)

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "ACCEPT_PUBLIC_GROUP_APPLY",
            targetType = "PUBLIC_GROUP_APPLY",
            targetId = applyId,
            details = "通过公开群组申请: 群组=${apply.groupName}, 申请人=${apply.applicantName}"
        )

        return Result.success(updatedApply)
    }

    fun rejectApply(
        applyId: String,
        adminId: String,
        adminUsername: String
    ): Result<PublicGroupApply> {
        val apply = publicGroupApplyRepository.findById(applyId).orElse(null)
            ?: return Result.failure(IllegalArgumentException("申请记录不存在"))

        if (apply.status != 0) {
            return Result.failure(IllegalStateException("该申请已被处理"))
        }

        val now = System.currentTimeMillis()
        val updatedApply = publicGroupApplyRepository.save(
            apply.copy(status = 2, reviewedBy = adminId, reviewedAt = now)
        )

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "REJECT_PUBLIC_GROUP_APPLY",
            targetType = "PUBLIC_GROUP_APPLY",
            targetId = applyId,
            details = "拒绝公开群组申请: 群组=${apply.groupName}, 申请人=${apply.applicantName}"
        )

        return Result.success(updatedApply)
    }

    fun topGroup(
        groupId: String,
        adminId: String,
        adminUsername: String
    ): Result<Unit> {
        val conversation = conversationRepository.findById(groupId).orElse(null)
            ?: return Result.failure(IllegalArgumentException("群组不存在"))

        if (conversation.publicState != 2) {
            return Result.failure(IllegalStateException("该群组不是公开群组，无法置顶"))
        }

        updatePublicState(groupId, 3)

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "TOP_PUBLIC_GROUP",
            targetType = "CONVERSATION",
            targetId = groupId,
            details = "置顶公开群组: ${conversation.name}"
        )

        return Result.success(Unit)
    }

    fun cancelTopGroup(
        groupId: String,
        adminId: String,
        adminUsername: String
    ): Result<Unit> {
        val conversation = conversationRepository.findById(groupId).orElse(null)
            ?: return Result.failure(IllegalArgumentException("群组不存在"))

        if (conversation.publicState != 3) {
            return Result.failure(IllegalStateException("该群组未被置顶"))
        }

        updatePublicState(groupId, 2)

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "CANCEL_TOP_PUBLIC_GROUP",
            targetType = "CONVERSATION",
            targetId = groupId,
            details = "取消置顶公开群组: ${conversation.name}"
        )

        return Result.success(Unit)
    }

    fun closePublicGroup(
        groupId: String,
        adminId: String,
        adminUsername: String
    ): Result<Unit> {
        val conversation = conversationRepository.findById(groupId).orElse(null)
            ?: return Result.failure(IllegalArgumentException("群组不存在"))

        if (conversation.publicState == 0) {
            return Result.failure(IllegalStateException("该群组不是公开群组"))
        }

        updatePublicState(groupId, 0)

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "CLOSE_PUBLIC_GROUP",
            targetType = "CONVERSATION",
            targetId = groupId,
            details = "取消公开群组: ${conversation.name}"
        )

        return Result.success(Unit)
    }
}
