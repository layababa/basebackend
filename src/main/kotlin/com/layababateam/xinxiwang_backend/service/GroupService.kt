package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.ConversationType
import com.layababateam.xinxiwang_backend.model.JoinMode
import com.layababateam.xinxiwang_backend.model.PinnedMessage
import com.layababateam.xinxiwang_backend.model.GroupJoinRequest
import com.layababateam.xinxiwang_backend.model.UserConversation
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.MessageRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class GroupService(
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val userSessionManager: UserSessionManager,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val redisTemplate: StringRedisTemplate,
    private val conversationCacheService: ConversationCacheService,
    private val userCacheService: UserCacheService,
    private val groupNotificationService: GroupNotificationService,
    private val groupSettingsService: GroupSettingsService,
    private val groupJoinRequestService: GroupJoinRequestService,
    private val groupUserPreferenceService: GroupUserPreferenceService,
    private val objectMapper: ObjectMapper,
    private val userConversationCacheService: UserConversationCacheService,
    private val mongoTemplate: MongoTemplate,
    private val deviceReadPointRepository: com.layababateam.xinxiwang_backend.repository.DeviceReadPointRepository,
    @Value("\${rentmsg.media.proxy.public-base}") private val proxyPublicBase: String,
) {
    private val log = LoggerFactory.getLogger(GroupService::class.java)

    // ── 建群 ──

    fun createGroup(
        creatorId: String,
        name: String,
        avatarUrl: String?,
        memberIds: List<String>,
        joinMode: Int = 0,
        maxMembers: Int = 5000
    ): Conversation {
        val allMembers = (memberIds + creatorId).distinct()
        require(allMembers.size <= maxMembers) { "成员数超过上限" }

        val now = System.currentTimeMillis()
        val systemContent = "群聊创建成功，我们来聊天吧"
        val conv = conversationRepository.save(
            Conversation(
                type = ConversationType.GROUP.value,
                members = allMembers,
                name = name,
                avatarUrl = avatarUrl,
                ownerId = creatorId,
                adminIds = listOf(creatorId),
                joinMode = joinMode,
                maxMembers = maxMembers,
                createdAt = now,
                lastMessageContent = systemContent,
                lastMessageContentType = 6,
                lastMessageSenderId = creatorId,
                lastMessageTime = now
            )
        )
        conversationCacheService.invalidate(conv.id!!)

        val existingUcs = userConversationRepository.findByConversationId(conv.id!!)
            .map { it.userId }.toSet()
        val ucs = allMembers.filter { it !in existingUcs }.map { uid ->
            UserConversation(
                userId = uid,
                conversationId = conv.id!!,
                createdAt = now
            )
        }
        if (ucs.isNotEmpty()) {
            userConversationRepository.saveAll(ucs)
            userConversationCacheService.invalidateAll(allMembers)
        }

        val creator = userCacheService.getUser(creatorId)
        val memberUsers = userCacheService.getUsers(allMembers)
        val membersDetail = allMembers.map { uid ->
            val u = memberUsers[uid]
            mapOf(
                "userId" to uid,
                "displayName" to u?.displayName,
                "avatarUrl" to u?.avatarUrl,
                "role" to when {
                    uid == creatorId -> 2
                    else -> 0
                }
            )
        }
        publishGroupEvent("group_created", allMembers, mapOf(
            "conversationId" to conv.id,
            "name" to name,
            "avatarUrl" to avatarUrl,
            "ownerId" to creatorId,
            "ownerName" to creator?.displayName,
            "memberCount" to allMembers.size,
            "members" to membersDetail,
            "lastMessageTime" to now,
            "lastMessageContent" to systemContent,
            "lastMessageContentType" to 6
        ))

        log.info("Group created: {} by user {}, members={}", conv.id, creatorId, allMembers.size)

        groupNotificationService.sendSystemMessage(creatorId, conv.id!!, "群聊创建成功，我们来聊天吧")

        return conv
    }

    // ── 邀请成员 ──

    fun inviteMembers(operatorId: String, convId: String, memberIds: List<String>) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)

        when (conv.joinMode) {
            JoinMode.MEMBER_INVITE.value -> require(operatorId in conv.members) { "仅群成员可邀请" }
            JoinMode.ADMIN_ONLY.value -> requireAdmin(conv, operatorId)
            // 0 = 不限制
        }

        val newMembers = memberIds.filter { it !in conv.members }
        require(conv.members.size + newMembers.size <= conv.maxMembers) { "成员数已达上限" }
        if (newMembers.isEmpty()) return

        val now = System.currentTimeMillis()
        val updatedMembers = conv.members + newMembers
        updateConvFields(convId, mapOf("members" to updatedMembers))
        invalidateMemberCache(convId)

        val existingUcs = userConversationRepository.findByConversationId(convId)
            .filter { it.userId in newMembers }.map { it.userId }.toSet()
        val ucs = newMembers.filter { it !in existingUcs }.map { uid ->
            UserConversation(userId = uid, conversationId = convId, createdAt = now)
        }
        if (ucs.isNotEmpty()) {
            userConversationRepository.saveAll(ucs)
            userConversationCacheService.invalidateAll(newMembers)
        }

        val newUsers = userCacheService.getUsers(newMembers)
        val eventPayload = mutableMapOf<String, Any?>(
            "conversationId" to convId,
            "newMembers" to newMembers.map { uid ->
                mapOf("userId" to uid, "displayName" to newUsers[uid]?.displayName, "avatarUrl" to newUsers[uid]?.avatarUrl)
            },
            "memberCount" to updatedMembers.size,
            "groupName" to conv.name,
            "groupAvatar" to conv.avatarUrl,
            "ownerId" to conv.ownerId
        )
        // 携带群公告，确保新成员首次进群时可获取历史公告
        if (conv.announcement.isNotEmpty()) {
            eventPayload["announcement"] = conv.announcement
        }
        publishGroupEvent("group_member_joined", updatedMembers, eventPayload)

        // Send system notification in-group
        val names = newMembers.mapNotNull { newUsers[it]?.displayName ?: it }.joinToString(", ")
        groupNotificationService.sendSystemMessage(operatorId, convId, "\"$names\" 加入了群聊")
    }

    // ── 踢出成员 ──

    fun kickMember(operatorId: String, convId: String, targetId: String) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        requireAdmin(conv, operatorId)
        require(targetId != conv.ownerId) { "不能踢出群主" }
        require(targetId in conv.members) { "用户不在群中" }

        val updatedMembers = conv.members - targetId
        updateConvFields(convId, mapOf(
            "members" to updatedMembers,
            "adminIds" to (conv.adminIds - targetId),
            "mutedMembers" to (conv.mutedMembers - targetId)
        ))
        invalidateMemberCache(convId)
        userConversationRepository.deleteByUserIdAndConversationId(targetId, convId)
        deviceReadPointRepository.deleteByUserIdAndConversationId(targetId, convId)
        userConversationCacheService.invalidate(targetId)
        redisTemplate.opsForHash<String, String>().delete("rentmsg:group:read:$convId", targetId)

        pushMemberLeft(convId, targetId, "kick", updatedMembers + targetId)
    }

    fun kickBatchInactive(operatorId: String, convId: String, userIds: List<String>) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        requireAdmin(conv, operatorId)

        val toKick = userIds.filter { it in conv.members && it != conv.ownerId }
        if (toKick.isEmpty()) return

        val toKickSet = toKick.toSet()
        val updatedMembers = conv.members - toKickSet
        updateConvFields(convId, mapOf(
            "members" to updatedMembers,
            "adminIds" to (conv.adminIds - toKickSet),
            "mutedMembers" to (conv.mutedMembers - toKickSet)
        ))
        invalidateMemberCache(convId)
        userConversationRepository.deleteAllByUserIdInAndConversationId(toKick, convId)
        deviceReadPointRepository.deleteByUserIdInAndConversationId(toKick, convId)
        userConversationCacheService.invalidateAll(toKick)
        if (toKick.isNotEmpty()) {
            redisTemplate.opsForHash<String, String>().delete(
                "rentmsg:group:read:$convId", *toKick.toTypedArray()
            )
        }

        val allNotify = updatedMembers + toKick
        toKick.forEach { pushMemberLeft(convId, it, "kick", allNotify) }
    }

    // ── 退群 ──

    fun quitGroup(userId: String, convId: String) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        require(userId != conv.ownerId) { "群主不能退群，请先转让或解散" }
        require(userId in conv.members) { "您不在此群" }

        val updatedMembers = conv.members - userId
        updateConvFields(convId, mapOf(
            "members" to updatedMembers,
            "adminIds" to (conv.adminIds - userId),
            "mutedMembers" to (conv.mutedMembers - userId)
        ))
        invalidateMemberCache(convId)
        userConversationRepository.deleteByUserIdAndConversationId(userId, convId)
        deviceReadPointRepository.deleteByUserIdAndConversationId(userId, convId)
        userConversationCacheService.invalidate(userId)
        redisTemplate.opsForHash<String, String>().delete("rentmsg:group:read:$convId", userId)

        pushMemberLeft(convId, userId, "quit", updatedMembers + userId)
    }

    // ── 解散群聊 ──

    fun disbandGroup(ownerId: String, convId: String) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        require(ownerId == conv.ownerId) { "仅群主可解散群聊" }

        publishGroupEvent("group_disbanded", conv.members, mapOf("conversationId" to convId))

        userConversationCacheService.invalidateAll(conv.members)
        userConversationRepository.deleteByConversationId(convId)
        deviceReadPointRepository.deleteByConversationId(convId)
        conversationRepository.deleteById(convId)
        conversationCacheService.invalidate(convId)
        invalidateMemberCache(convId)
        redisTemplate.delete("rentmsg:group:read:$convId")
        redisTemplate.delete("rentmsg:seq:$convId")
        log.info("Group {} disbanded by owner {}", convId, ownerId)
    }

    // ── 转让群主 ──

    fun transferOwner(ownerId: String, convId: String, newOwnerId: String) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        require(ownerId == conv.ownerId) { "仅群主可转让" }
        require(newOwnerId in conv.members) { "新群主必须在群中" }

        val updatedAdmins = ((conv.adminIds - ownerId) + newOwnerId).distinct()
        updateConvFields(convId, mapOf(
            "ownerId" to newOwnerId,
            "adminIds" to updatedAdmins
        ))
        invalidateMemberCache(convId)

        pushSettingsUpdate(convId, conv.members, mapOf(
            "ownerId" to newOwnerId,
            "adminIds" to updatedAdmins
        ))

        val newOwner = userCacheService.getUser(newOwnerId)
        groupNotificationService.sendSystemMessage(
            ownerId, convId,
            "\"${newOwner?.displayName ?: newOwnerId}\" 已成为新群主"
        )
    }

    // ── 加群申请（委派至 GroupJoinRequestService）──

    fun applyJoinGroup(applicantId: String, convId: String, message: String): GroupJoinRequest =
        groupJoinRequestService.applyJoinGroup(applicantId, convId, message)

    fun approveJoinRequest(operatorId: String, requestId: String) =
        groupJoinRequestService.approveJoinRequest(operatorId, requestId)

    fun rejectJoinRequest(operatorId: String, requestId: String) =
        groupJoinRequestService.rejectJoinRequest(operatorId, requestId)

    fun getJoinRequests(operatorId: String, convId: String): List<Map<String, Any?>> =
        groupJoinRequestService.getJoinRequests(operatorId, convId)

    // ── 群设置（委派至 GroupSettingsService）──

    fun updateGroupInfo(operatorId: String, convId: String, name: String?, avatarUrl: String?) =
        groupSettingsService.updateGroupInfo(operatorId, convId, name, avatarUrl)

    fun setAnnouncement(operatorId: String, convId: String, content: String) =
        groupSettingsService.setAnnouncement(operatorId, convId, content)

    fun deleteAnnouncement(operatorId: String, convId: String, announcementId: String) =
        groupSettingsService.deleteAnnouncement(operatorId, convId, announcementId)

    fun setMuteAll(operatorId: String, convId: String, mute: Boolean) =
        groupSettingsService.setMuteAll(operatorId, convId, mute)

    fun muteMember(operatorId: String, convId: String, targetId: String, mute: Boolean) =
        groupSettingsService.muteMember(operatorId, convId, targetId, mute)

    fun setBlockLinks(operatorId: String, convId: String, block: Boolean) =
        groupSettingsService.setBlockLinks(operatorId, convId, block)

    fun setAddFriendMode(operatorId: String, convId: String, mode: Int) =
        groupSettingsService.setAddFriendMode(operatorId, convId, mode)

    fun setJoinMode(operatorId: String, convId: String, mode: Int) =
        groupSettingsService.setJoinMode(operatorId, convId, mode)

    fun setSearchable(operatorId: String, convId: String, value: Boolean) =
        groupSettingsService.setSearchable(operatorId, convId, value)

    fun setHistoryVisible(operatorId: String, convId: String, value: Boolean) =
        groupSettingsService.setHistoryVisible(operatorId, convId, value)

    fun setMaxMembers(ownerId: String, convId: String, max: Int) =
        groupSettingsService.setMaxMembers(ownerId, convId, max)

    // ── 置顶消息 ──

    fun pinMessage(operatorId: String, convId: String, messageId: String) {
        val conv = getConvOrThrow(convId)
        require(operatorId in conv.members) { "您不在此会话中" }

        if (conv.type == ConversationType.GROUP.value) {
            requireAdmin(conv, operatorId)
        }

        require(conv.pinnedMessages.none { it.messageId == messageId }) { "该消息已被置顶" }
        require(conv.pinnedMessages.size < 5) { "最多只能置顶 5 条消息" }

        val msg = messageRepository.findById(messageId).orElse(null)
            ?: throw IllegalArgumentException("消息不存在")
        require(msg.conversationId == convId) { "消息不属于此会话" }

        val sender = userCacheService.getUser(msg.senderId)
        val now = System.currentTimeMillis()
        val normalizedContent = MediaContentUrlNormalizer.normalize(
            msg.content,
            msg.contentType,
            objectMapper,
            videoCompatPublicBase = proxyPublicBase,
        )

        val newPinned = PinnedMessage(
            messageId = messageId,
            content = normalizedContent,
            contentType = msg.contentType,
            senderId = msg.senderId,
            senderName = sender?.displayName,
            seqId = msg.seqId,
            pinnedBy = operatorId,
            pinnedAt = now
        )
        val updatedList = listOf(newPinned) + conv.pinnedMessages

        updateConvFields(convId, mapOf("pinnedMessages" to updatedList))

        val payload = mapOf(
            "conversationId" to convId,
            "pinnedMessage" to mapOf(
                "messageId" to messageId, "content" to normalizedContent,
                "contentType" to msg.contentType, "senderName" to sender?.displayName,
                "seqId" to msg.seqId, "pinnedBy" to operatorId, "pinnedAt" to now
            ),
            "allPinned" to pinnedListToPayload(updatedList)
        )
        publishGroupEvent("message_pinned", conv.members, payload)
        log.info("Message {} pinned in conversation {} by {} (total: {})", messageId, convId, operatorId, updatedList.size)
    }

    fun unpinMessage(operatorId: String, convId: String, messageId: String) {
        val conv = getConvOrThrow(convId)
        require(operatorId in conv.members) { "您不在此会话中" }

        if (conv.type == ConversationType.GROUP.value) {
            requireAdmin(conv, operatorId)
        }

        val updatedList = conv.pinnedMessages.filter { it.messageId != messageId }
        updateConvFields(convId, mapOf("pinnedMessages" to updatedList))

        val payload = mapOf(
            "conversationId" to convId,
            "messageId" to messageId,
            "allPinned" to pinnedListToPayload(updatedList)
        )
        publishGroupEvent("message_unpinned", conv.members, payload)
        log.info("Message {} unpinned in conversation {} by {}", messageId, convId, operatorId)
    }

    fun removePinnedMessageIfPresent(convId: String, messageId: String) {
        val conv = conversationCacheService.getConversation(convId) ?: return
        if (conv.pinnedMessages.none { it.messageId == messageId }) return

        val updatedList = conv.pinnedMessages.filter { it.messageId != messageId }
        updateConvFields(convId, mapOf("pinnedMessages" to updatedList))

        val payload = mapOf(
            "conversationId" to convId,
            "messageId" to messageId,
            "allPinned" to pinnedListToPayload(updatedList)
        )
        publishGroupEvent("message_unpinned", conv.members, payload)
    }

    private fun pinnedListToPayload(list: List<PinnedMessage>): List<Map<String, Any?>> =
        list.map { pm ->
            mapOf(
                "messageId" to pm.messageId,
                "content" to MediaContentUrlNormalizer.normalizeNullable(
                    pm.content,
                    pm.contentType,
                    objectMapper,
                    videoCompatPublicBase = proxyPublicBase,
                ),
                "contentType" to pm.contentType, "senderName" to pm.senderName,
                "seqId" to pm.seqId, "pinnedBy" to pm.pinnedBy, "pinnedAt" to pm.pinnedAt
            )
        }

    // ── 个人群设置（委派至 GroupUserPreferenceService）──

    fun updateMyNickname(userId: String, convId: String, nickname: String?) =
        groupUserPreferenceService.updateMyNickname(userId, convId, nickname)

    fun updateGroupRemark(userId: String, convId: String, remark: String?) =
        groupUserPreferenceService.updateGroupRemark(userId, convId, remark)

    fun saveToContacts(userId: String, convId: String, save: Boolean) =
        groupUserPreferenceService.saveToContacts(userId, convId, save)

    // ── 查询 ──

    fun getGroupSettings(convId: String): Map<String, Any?> {
        val conv = getConvOrThrow(convId)
        return mapOf(
            "muteAll" to conv.muteAll,
            "blockLinks" to conv.blockLinks,
            "mutedMembers" to conv.mutedMembers,
            "addFriendMode" to conv.addFriendMode
        )
    }

    fun getGroupMembers(convId: String): List<Map<String, Any?>> {
        // 缓存完整的组装结果（60s），避免 200 人群 × 200 次 Redis GET + DB 查询
        val cacheKey = "rentmsg:group_members:$convId"
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) {
                return objectMapper.readValue(cached,
                    object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any?>>>() {})
            }
        } catch (e: Exception) {
            log.debug("[GroupMembers] cache read error: {}", e.message)
        }

        val conv = getConvOrThrow(convId)
        val users = userCacheService.getUsers(conv.members)
        val ucs = userConversationRepository.findByConversationId(convId).associateBy { it.userId }

        val result = conv.members.map { uid ->
            val u = users[uid]
            val uc = ucs[uid]
            mapOf(
                "userId" to uid,
                "displayName" to u?.displayName,
                "avatarUrl" to u?.avatarUrl,
                "myNickname" to uc?.myNickname,
                "role" to when {
                    uid == conv.ownerId -> 2
                    uid in conv.adminIds -> 1
                    else -> 0
                },
                "lastActiveAt" to (uc?.lastActiveAt ?: 0L),
                "isMuted" to (uid in conv.mutedMembers)
            )
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                java.time.Duration.ofSeconds(60))
        } catch (_: Exception) {}

        return result
    }

    fun searchGroups(keyword: String): List<Map<String, Any?>> {
        if (keyword.isBlank()) return emptyList()
        val groups = conversationRepository.findByTypeAndSearchableAndNameContainingIgnoreCase(ConversationType.GROUP.value, true, keyword)
        return groups.map { conv ->
            mapOf(
                "conversationId" to conv.id,
                "name" to conv.name,
                "avatarUrl" to conv.avatarUrl,
                "memberCount" to conv.members.size
            )
        }
    }

    fun getGroupReadStatus(convId: String): Map<String, Long> {
        val entries = redisTemplate.opsForHash<String, String>().entries("rentmsg:group:read:$convId")
        return entries.mapValues { it.value.toLongOrNull() ?: 0L }
    }

    // ── Admin helpers ──

    fun setAdmin(operatorId: String, convId: String, targetId: String, isAdmin: Boolean) {
        val conv = getConvOrThrow(convId)
        require(operatorId == conv.ownerId) { "仅群主可设置管理员" }
        require(targetId in conv.members) { "用户不在群中" }
        val updated = if (isAdmin) (conv.adminIds + targetId).distinct() else conv.adminIds - targetId
        updateConvFields(convId, mapOf("adminIds" to updated))
        invalidateMemberCache(convId)
        pushSettingsUpdate(convId, conv.members, mapOf("adminIds" to updated))

        // Send system notification in-group
        val targetUser = userCacheService.getUser(targetId)
        val targetName = targetUser?.displayName ?: targetId
        val notice = if (isAdmin) "“$targetName” 已被设为管理员" else "“$targetName” 的管理员身份已被撤销"
        groupNotificationService.sendSystemMessage(operatorId, convId, notice)
    }

    // ── Redis member cache ──

    fun getCachedMembers(convId: String): List<String> {
        val cacheKey = "rentmsg:group:members:$convId"
        val cached = redisTemplate.opsForSet().members(cacheKey)
        if (!cached.isNullOrEmpty()) return cached.toList()

        val conv = conversationCacheService.getConversation(convId) ?: return emptyList()
        if (conv.members.isNotEmpty()) {
            redisTemplate.opsForSet().add(cacheKey, *conv.members.toTypedArray())
            redisTemplate.expire(cacheKey, java.time.Duration.ofMinutes(10))
        }
        return conv.members
    }

    private fun invalidateMemberCache(convId: String) {
        redisTemplate.delete("rentmsg:group:members:$convId")
        redisTemplate.delete("rentmsg:group_members:$convId")
    }

    // ── Private helpers ──

    /**
     * 用 MongoTemplate $set 部分更新会话字段，避免 save(conv.copy(...)) 那种
     * 基于陈旧缓存快照的全量回写把并发写入的其他字段（例如 addFriendMode）
     * 静默刷回默认值。只触碰 fields 里明确指定的列。
     */
    private fun updateConvFields(convId: String, fields: Map<String, Any?>) {
        if (fields.isEmpty()) return
        val update = Update()
        fields.forEach { (k, v) -> update.set(k, v) }
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(convId)),
            update,
            Conversation::class.java
        )
        conversationCacheService.invalidate(convId)
    }

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
            "group_event:$type members=${memberIds.size} conv=${data["conversationId"] ?: "-"}",
        )
    }

    private fun pushMemberLeft(convId: String, userId: String, reason: String, notifyMembers: List<String>) {
        val user = userCacheService.getUser(userId)
        publishGroupEvent("group_member_left", notifyMembers, mapOf(
            "conversationId" to convId,
            "userId" to userId,
            "displayName" to user?.displayName,
            "reason" to reason
        ))
    }

    private fun pushSettingsUpdate(convId: String, members: List<String>, changes: Map<String, Any?>) {
        publishGroupEvent("group_settings_updated", members, changes + ("conversationId" to convId))
    }
}
