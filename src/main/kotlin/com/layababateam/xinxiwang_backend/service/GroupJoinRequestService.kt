package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.ConversationType
import com.layababateam.xinxiwang_backend.model.GroupJoinRequest
import com.layababateam.xinxiwang_backend.model.UserConversation
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.GroupJoinRequestRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * 群組加入申請服務
 *
 * 負責群聊的加入申請流程：申請、審核通過、拒絕、查詢待審核列表。
 */
@Service
class GroupJoinRequestService(
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val groupJoinRequestRepository: GroupJoinRequestRepository,
    private val conversationCacheService: ConversationCacheService,
    private val userCacheService: UserCacheService,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val redisTemplate: StringRedisTemplate,
    private val groupNotificationService: GroupNotificationService,
    private val userConversationCacheService: UserConversationCacheService,
    private val mongoTemplate: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(GroupJoinRequestService::class.java)

    fun applyJoinGroup(applicantId: String, convId: String, message: String): GroupJoinRequest {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        require(applicantId !in conv.members) { "您已在群中" }
        require(conv.members.size < conv.maxMembers) { "成员数已满" }

        val existing = groupJoinRequestRepository.findByApplicantIdAndConversationIdAndStatus(applicantId, convId, 0)
        if (existing != null) throw IllegalStateException("已有待审核的申请")

        val req = groupJoinRequestRepository.save(
            GroupJoinRequest(conversationId = convId, applicantId = applicantId, message = message)
        )

        val applicant = userCacheService.getUser(applicantId)
        val admins = (conv.adminIds + conv.ownerId).filterNotNull().distinct()
        val pendingCount = groupJoinRequestRepository.findByConversationIdAndStatus(convId, 0).size
        publishGroupEvent("group_join_request_notification", admins, mapOf(
            "requestId" to req.id,
            "conversationId" to convId,
            "groupName" to conv.name,
            "applicantId" to applicantId,
            "applicantName" to applicant?.displayName,
            "applicantAvatar" to applicant?.avatarUrl,
            "message" to message,
            "pendingCount" to pendingCount
        ))

        return req
    }

    fun approveJoinRequest(operatorId: String, requestId: String) {
        val req = groupJoinRequestRepository.findById(requestId).orElseThrow { IllegalArgumentException("申请不存在") }
        require(req.status == 0) { "申请已处理" }
        val conv = getConvOrThrow(req.conversationId)
        requireAdmin(conv, operatorId)
        require(conv.members.size < conv.maxMembers) { "成员数已满" }

        val now = System.currentTimeMillis()
        groupJoinRequestRepository.save(req.copy(status = 1, handledAt = now, handledBy = operatorId))

        val updatedMembers = conv.members + req.applicantId
        // $set 部分更新，只触碰 members，避免全量回写把 addFriendMode 等并发改动的字段刷回默认值
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(req.conversationId)),
            Update().set("members", updatedMembers),
            Conversation::class.java
        )
        conversationCacheService.invalidate(req.conversationId)
        invalidateMemberCache(req.conversationId)
        if (userConversationRepository.findFirstByUserIdAndConversationId(req.applicantId, req.conversationId) == null) {
            userConversationRepository.save(
                UserConversation(userId = req.applicantId, conversationId = req.conversationId, createdAt = now)
            )
            userConversationCacheService.invalidate(req.applicantId)
        }

        val applicant = userCacheService.getUser(req.applicantId)
        val joinedPayload = mutableMapOf<String, Any?>(
            "conversationId" to req.conversationId,
            "newMembers" to listOf(mapOf(
                "userId" to req.applicantId,
                "displayName" to applicant?.displayName,
                "avatarUrl" to applicant?.avatarUrl
            )),
            "memberCount" to updatedMembers.size,
            "groupName" to conv.name,
            "groupAvatar" to conv.avatarUrl,
            "ownerId" to conv.ownerId
        )
        // 携带群公告，确保新成员首次进群时可获取历史公告
        if (conv.announcement.isNotEmpty()) {
            joinedPayload["announcement"] = conv.announcement
        }
        publishGroupEvent("group_member_joined", updatedMembers, joinedPayload)
        publishGroupEvent("group_join_approved", listOf(req.applicantId), mapOf(
            "conversationId" to req.conversationId,
            "groupName" to conv.name,
            "groupAvatar" to conv.avatarUrl,
            "ownerId" to conv.ownerId,
            "memberCount" to updatedMembers.size
        ))

        // Send system notification in-group
        val applicantName = applicant?.displayName ?: req.applicantId
        groupNotificationService.sendSystemMessage(operatorId, req.conversationId, "\"$applicantName\" 加入了群聊")

        // Push updated pending count to admins
        val remainingCount = groupJoinRequestRepository.findByConversationIdAndStatus(req.conversationId, 0).size
        val adminList = (conv.adminIds + conv.ownerId).filterNotNull().distinct()
        publishGroupEvent("group_join_request_count_updated", adminList, mapOf(
            "conversationId" to req.conversationId,
            "pendingCount" to remainingCount
        ))
    }

    fun rejectJoinRequest(operatorId: String, requestId: String) {
        val req = groupJoinRequestRepository.findById(requestId).orElseThrow { IllegalArgumentException("申请不存在") }
        require(req.status == 0) { "申请已处理" }
        val conv = getConvOrThrow(req.conversationId)
        requireAdmin(conv, operatorId)

        groupJoinRequestRepository.save(req.copy(status = 2, handledAt = System.currentTimeMillis(), handledBy = operatorId))
        publishGroupEvent("group_join_rejected", listOf(req.applicantId), mapOf(
            "conversationId" to req.conversationId, "groupName" to conv.name
        ))

        // Push updated pending count to admins
        val remainingCount = groupJoinRequestRepository.findByConversationIdAndStatus(req.conversationId, 0).size
        val adminList = (conv.adminIds + conv.ownerId).filterNotNull().distinct()
        publishGroupEvent("group_join_request_count_updated", adminList, mapOf(
            "conversationId" to req.conversationId,
            "pendingCount" to remainingCount
        ))
    }

    fun getJoinRequests(operatorId: String, convId: String): List<Map<String, Any?>> {
        val conv = getConvOrThrow(convId)
        // 非管理员直接返回空列表，避免抛异常导致前端弹出错误提示
        if (operatorId != conv.ownerId && operatorId !in conv.adminIds) return emptyList()
        val reqs = groupJoinRequestRepository.findByConversationIdAndStatus(convId, 0)
        val users = userCacheService.getUsers(reqs.map { it.applicantId })
        return reqs.map { r ->
            mapOf(
                "requestId" to r.id,
                "applicantId" to r.applicantId,
                "applicantName" to users[r.applicantId]?.displayName,
                "applicantAvatar" to users[r.applicantId]?.avatarUrl,
                "message" to r.message,
                "createdAt" to r.createdAt
            )
        }
    }

    // ── Private helpers ──

    private fun getConvOrThrow(convId: String): Conversation {
        return conversationCacheService.getConversation(convId)
            ?: throw IllegalArgumentException("会话不存在")
    }

    private fun requireGroup(conv: Conversation) {
        require(conv.type == ConversationType.GROUP.value) { "此操作仅适用于群聊" }
    }

    private fun requireAdmin(conv: Conversation, userId: String) {
        require(userId == conv.ownerId || userId in conv.adminIds) { "需要管理员权限" }
    }

    private fun publishGroupEvent(type: String, memberIds: List<String>, data: Map<String, Any?>) {
        rabbitPublishService.send(
            RabbitMQConfig.EVENT_GROUP_EXCHANGE,
            "",
            mapOf("type" to type, "memberIds" to memberIds, "data" to data),
            "group_join_request_event:$type members=${memberIds.size} conv=${data["conversationId"] ?: "-"}",
        )
    }

    private fun invalidateMemberCache(convId: String) {
        redisTemplate.delete("rentmsg:group:members:$convId")
    }
}
