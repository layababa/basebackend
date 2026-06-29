package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.extensions.escapeRegex
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class AdminGroupService(
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val userRepository: UserRepository,
    private val mongoTemplate: MongoTemplate,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val redisTemplate: StringRedisTemplate,
    private val conversationCacheService: ConversationCacheService,
    private val userCacheService: UserCacheService,
    private val groupNotificationService: GroupNotificationService,
    private val auditLogService: AuditLogService,
    private val userConversationCacheService: UserConversationCacheService,
    private val deviceReadPointRepository: com.layababateam.xinxiwang_backend.repository.DeviceReadPointRepository
) {
    private val log = LoggerFactory.getLogger(AdminGroupService::class.java)

    fun listGroups(page: Int, size: Int, keyword: String?): Map<String, Any?> {
        val pageSize = size.coerceIn(1, 100)
        val query = Query(Criteria.where("type").`is`(1))

        if (!keyword.isNullOrBlank()) {
            val safeKeyword = keyword.escapeRegex()
            val ownerIds = findOwnerIdsByKeyword(safeKeyword)
            val criteria = Criteria().orOperator(
                Criteria.where("name").regex(safeKeyword, "i"),
                Criteria.where("ownerId").`in`(ownerIds)
            )
            query.addCriteria(criteria)
        }

        val total = mongoTemplate.count(query, Conversation::class.java)
        query.with(PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")))
        val groups = mongoTemplate.find(query, Conversation::class.java)

        val ownerIds = groups.mapNotNull { it.ownerId }.distinct()
        val owners = userCacheService.getUsers(ownerIds)

        val items = groups.map { conv ->
            val owner = conv.ownerId?.let { owners[it] }
            mapOf(
                "id" to conv.id,
                "name" to conv.name,
                "avatarUrl" to conv.avatarUrl,
                "ownerId" to conv.ownerId,
                "ownerName" to owner?.displayName,
                "memberCount" to conv.members.size,
                "createdAt" to conv.createdAt,
                "muteAll" to conv.muteAll
            )
        }

        return mapOf(
            "items" to items,
            "total" to total,
            "page" to page,
            "size" to pageSize
        )
    }

    fun getGroupDetail(groupId: String): Map<String, Any?>? {
        val conv = conversationCacheService.getConversation(groupId) ?: return null
        if (conv.type != 1) return null

        val owner = conv.ownerId?.let { userCacheService.getUser(it) }

        return mapOf(
            "id" to conv.id,
            "name" to conv.name,
            "avatarUrl" to conv.avatarUrl,
            "ownerId" to conv.ownerId,
            "ownerName" to owner?.displayName,
            "ownerAvatarUrl" to owner?.avatarUrl,
            "adminIds" to conv.adminIds,
            "memberCount" to conv.members.size,
            "joinMode" to conv.joinMode,
            "searchable" to conv.searchable,
            "maxMembers" to conv.maxMembers,
            "historyVisible" to conv.historyVisible,
            "muteAll" to conv.muteAll,
            "blockLinks" to conv.blockLinks,
            "addFriendMode" to GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
            "announcement" to conv.announcement,
            "announcementUpdatedAt" to conv.announcementUpdatedAt,
            "createdAt" to conv.createdAt
        )
    }

    fun getGroupMembers(groupId: String, page: Int, size: Int): Map<String, Any?>? {
        val conv = conversationCacheService.getConversation(groupId) ?: return null
        if (conv.type != 1) return null

        val pageSize = size.coerceIn(1, 100)
        val allMembers = conv.members
        val total = allMembers.size
        val start = (page * pageSize).coerceAtMost(total)
        val end = (start + pageSize).coerceAtMost(total)
        val pagedMemberIds = allMembers.subList(start, end)

        val users = userCacheService.getUsers(pagedMemberIds)

        val items = pagedMemberIds.map { uid ->
            val u = users[uid]
            mapOf(
                "userId" to uid,
                "username" to u?.username,
                "displayName" to u?.displayName,
                "avatarUrl" to u?.avatarUrl,
                "role" to when {
                    uid == conv.ownerId -> 2
                    uid in conv.adminIds -> 1
                    else -> 0
                }
            )
        }

        return mapOf(
            "items" to items,
            "total" to total,
            "page" to page,
            "size" to pageSize
        )
    }

    fun updateGroupInfo(
        groupId: String,
        updates: Map<String, Any>,
        adminId: String,
        adminUsername: String
    ): Boolean {
        val conv = conversationCacheService.getConversation(groupId) ?: return false
        if (conv.type != 1) return false

        val allowedFields = setOf("name", "announcement", "muteAll", "blockLinks", "searchable", "maxMembers")
        val filtered = updates.filterKeys { it in allowedFields }
        if (filtered.isEmpty()) return false

        // 只 $set 调用方明确给出的字段，避免全量回写把 addFriendMode 等无关字段
        // 刷回默认值（基于陈旧缓存快照的写-写覆盖竞态）。
        val setFields = mutableMapOf<String, Any?>()
        filtered["name"]?.let { if (it is String) setFields["name"] = it }
        filtered["announcement"]?.let { if (it is String) setFields["announcement"] = it }
        filtered["muteAll"]?.let { if (it is Boolean) setFields["muteAll"] = it }
        filtered["blockLinks"]?.let { if (it is Boolean) setFields["blockLinks"] = it }
        filtered["searchable"]?.let { if (it is Boolean) setFields["searchable"] = it }
        filtered["maxMembers"]?.let { if (it is Number) setFields["maxMembers"] = it.toInt() }

        if (setFields.isNotEmpty()) {
            val update = Update()
            setFields.forEach { (k, v) -> update.set(k, v) }
            mongoTemplate.updateFirst(
                Query(Criteria.where("_id").`is`(groupId)),
                update,
                Conversation::class.java
            )
            conversationCacheService.invalidate(groupId)
        }

        publishGroupEvent("group_settings_updated", conv.members, filtered + ("conversationId" to groupId))

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "UPDATE_GROUP",
            targetType = "GROUP",
            targetId = groupId,
            details = "修改群信息: ${filtered.keys.joinToString(", ")}"
        )

        log.info("Admin {} updated group {} fields: {}", adminId, groupId, filtered.keys)
        return true
    }

    fun disbandGroup(groupId: String, adminId: String, adminUsername: String): Boolean {
        val conv = conversationCacheService.getConversation(groupId) ?: return false
        if (conv.type != 1) return false

        publishGroupEvent("group_disbanded", conv.members, mapOf("conversationId" to groupId))

        userConversationCacheService.invalidateAll(conv.members)
        userConversationRepository.deleteByConversationId(groupId)
        deviceReadPointRepository.deleteByConversationId(groupId)
        conversationRepository.deleteById(groupId)
        conversationCacheService.invalidate(groupId)
        redisTemplate.delete("rentmsg:group:read:$groupId")
        redisTemplate.delete("rentmsg:seq:$groupId")
        redisTemplate.delete("rentmsg:group:members:$groupId")

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "DISBAND_GROUP",
            targetType = "GROUP",
            targetId = groupId,
            details = "强制解散群组，原成员数: ${conv.members.size}"
        )

        log.info("Admin {} disbanded group {}, members={}", adminId, groupId, conv.members.size)
        return true
    }

    fun kickMember(groupId: String, targetUserId: String, adminId: String, adminUsername: String): String? {
        val conv = conversationCacheService.getConversation(groupId) ?: return "群组不存在"
        if (conv.type != 1) return "非群聊会话"
        if (targetUserId !in conv.members) return "用户不在群中"
        if (targetUserId == conv.ownerId) return "不能踢出群主"

        val updatedMembers = conv.members - targetUserId
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(groupId)),
            Update()
                .set("members", updatedMembers)
                .set("adminIds", conv.adminIds - targetUserId)
                .set("mutedMembers", conv.mutedMembers - targetUserId),
            Conversation::class.java
        )
        conversationCacheService.invalidate(groupId)
        redisTemplate.delete("rentmsg:group:members:$groupId")
        userConversationRepository.deleteByUserIdAndConversationId(targetUserId, groupId)
        deviceReadPointRepository.deleteByUserIdAndConversationId(targetUserId, groupId)
        userConversationCacheService.invalidate(targetUserId)
        redisTemplate.opsForHash<String, String>().delete("rentmsg:group:read:$groupId", targetUserId)

        val user = userCacheService.getUser(targetUserId)
        publishGroupEvent("group_member_left", updatedMembers + targetUserId, mapOf(
            "conversationId" to groupId,
            "userId" to targetUserId,
            "displayName" to user?.displayName,
            "reason" to "admin_kick"
        ))

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "KICK_GROUP_MEMBER",
            targetType = "GROUP",
            targetId = groupId,
            details = "强制踢出成员: $targetUserId"
        )

        log.info("Admin {} kicked user {} from group {}", adminId, targetUserId, groupId)
        return null
    }

    fun transferOwner(groupId: String, newOwnerId: String, adminId: String, adminUsername: String): String? {
        val conv = conversationCacheService.getConversation(groupId) ?: return "群组不存在"
        if (conv.type != 1) return "非群聊会话"
        if (newOwnerId !in conv.members) return "新群主必须在群中"

        val oldOwnerId = conv.ownerId
        val updatedAdmins = ((conv.adminIds - (oldOwnerId ?: "")) + newOwnerId).distinct()
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(groupId)),
            Update()
                .set("ownerId", newOwnerId)
                .set("adminIds", updatedAdmins),
            Conversation::class.java
        )
        conversationCacheService.invalidate(groupId)
        redisTemplate.delete("rentmsg:group:members:$groupId")

        publishGroupEvent("group_settings_updated", conv.members, mapOf(
            "conversationId" to groupId,
            "ownerId" to newOwnerId,
            "adminIds" to updatedAdmins
        ))

        val newOwner = userCacheService.getUser(newOwnerId)
        groupNotificationService.sendSystemMessage(
            newOwnerId, groupId,
            "\"${newOwner?.displayName ?: newOwnerId}\" 已成为新群主（管理员操作）"
        )

        auditLogService.log(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "TRANSFER_GROUP_OWNER",
            targetType = "GROUP",
            targetId = groupId,
            details = "强制转让群主: $oldOwnerId -> $newOwnerId"
        )

        log.info("Admin {} transferred group {} ownership from {} to {}", adminId, groupId, oldOwnerId, newOwnerId)
        return null
    }

    private fun findOwnerIdsByKeyword(safeKeyword: String): List<String> {
        val query = Query(Criteria().orOperator(
            Criteria.where("username").regex(safeKeyword, "i"),
            Criteria.where("displayName").regex(safeKeyword, "i")
        ))
        query.fields().include("_id")
        query.limit(500)
        return mongoTemplate.find(query, Map::class.java, "users")
            .mapNotNull { it["_id"]?.toString() }
    }

    private fun publishGroupEvent(type: String, memberIds: List<String>, data: Map<String, Any?>) {
        rabbitPublishService.send(
            RabbitMQConfig.EVENT_GROUP_EXCHANGE,
            "",
            mapOf("type" to type, "memberIds" to memberIds, "data" to data),
            "admin_group_event:$type members=${memberIds.size} conv=${data["conversationId"] ?: "-"}",
        )
    }
}
