package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ConversationDto
import com.layababateam.xinxiwang_backend.dto.MessageDto
import com.layababateam.xinxiwang_backend.dto.PinnedMessageDto
import com.layababateam.xinxiwang_backend.extensions.isPrivateChat
import com.layababateam.xinxiwang_backend.model.ConversationType
import com.layababateam.xinxiwang_backend.model.UserConversation
import io.netty.channel.Channel
import com.layababateam.xinxiwang_backend.repository.*
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.FriendshipCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.serializer.RedisSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ConversationService(
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val redisTemplate: StringRedisTemplate,
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper,
    private val userCacheService: UserCacheService,
    private val conversationCacheService: ConversationCacheService,
    private val readPointFlushService: ReadPointFlushService,
    private val userConversationCacheService: UserConversationCacheService,
    private val deviceReadPointRepository: com.layababateam.xinxiwang_backend.repository.DeviceReadPointRepository,
    private val friendshipCacheService: FriendshipCacheService,
    @Value("\${rentmsg.media.proxy.public-base}") private val proxyPublicBase: String,
) : ConversationInfoPort {
    private val log = LoggerFactory.getLogger(ConversationService::class.java)

    companion object {
        private const val UNREAD_PREFIX = "rentmsg:unread:"
        private const val SEQ_PREFIX = "rentmsg:seq:"
        private const val USER_CONV_LIST_PREFIX = "rentmsg:uc_list:"
        private const val USER_CONV_PREFIX = "rentmsg:uc:"
        private const val UC_CACHE_TTL = 60L // seconds
        private val UNREAD_TTL: Duration = Duration.ofDays(7)
        /** Lua: atomic INCR + conditional EXPIRE (only set TTL on newly created key) */
        private val INCR_WITH_TTL_SCRIPT = DefaultRedisScript<Long>().apply {
            setScriptText("""
                local v = redis.call('INCR', KEYS[1])
                if v == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return v
            """.trimIndent())
            resultType = Long::class.java
        }
    }

    /**
     * 缓存 findByUserId：用户的所有会话 ID 列表（60s TTL）
     */
    private fun getCachedUserConversations(userId: String): List<UserConversation> {
        val cacheKey = "$USER_CONV_LIST_PREFIX$userId"
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) {
                val list = objectMapper.readValue(cached,
                    object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any?>>>() {})
                return list.map { m ->
                    UserConversation(
                        id = m["id"] as? String,
                        userId = m["userId"] as? String ?: userId,
                        conversationId = m["conversationId"] as? String ?: "",
                        lastReadTime = (m["lastReadTime"] as? Number)?.toLong() ?: 0L,
                        readSeqId = (m["readSeqId"] as? Number)?.toLong() ?: 0L,
                        muted = m["muted"] as? Boolean ?: false,
                        pinned = m["pinned"] as? Boolean ?: false,
                        createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                        myNickname = m["myNickname"] as? String,
                        groupRemark = m["groupRemark"] as? String,
                        savedToContacts = m["savedToContacts"] as? Boolean ?: false,
                        mentionedSeqIds = (m["mentionedSeqIds"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList(),
                        lastActiveAt = (m["lastActiveAt"] as? Number)?.toLong() ?: 0L,
                        peerRemark = m["peerRemark"] as? String,
                        hiddenBeforeSeqId = (m["hiddenBeforeSeqId"] as? Number)?.toLong() ?: 0L,
                        deleted = m["deleted"] as? Boolean ?: false
                    )
                }
            }
        } catch (e: Exception) {
            log.debug("[UC Cache] read error: {}", e.message)
        }

        val result = userConversationRepository.findByUserId(userId)
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                java.time.Duration.ofSeconds(UC_CACHE_TTL))
        } catch (_: Exception) {}
        return result
    }

    private fun invalidateUserConversationCache(userId: String) {
        userConversationCacheService.invalidate(userId)
    }

    fun getConversationList(userId: String, requesterDeviceId: String? = null): List<ConversationDto> {
        // 排除被用户软删除的会话（非好友状态下的"删除会话"）
        val userConversations = getCachedUserConversations(userId).filter { !it.deleted }
        if (userConversations.isEmpty()) return emptyList()

        val convIds = userConversations.map { it.conversationId }
        val conversations = conversationCacheService.getConversations(convIds)
        val ucMap = userConversations.associateBy { it.conversationId }

        // Per-device readSeqId 批量查询（单次 Mongo 查询，走复合唯一索引）
        val myDeviceReadMap: Map<String, Long> = if (!requesterDeviceId.isNullOrEmpty() && convIds.isNotEmpty()) {
            deviceReadPointRepository
                .findByUserIdAndDeviceIdAndConversationIdIn(userId, requesterDeviceId, convIds)
                .associate { it.conversationId to it.readSeqId }
        } else emptyMap()

        val peerUserIds = mutableSetOf<String>()
        val peerUserIdMap = mutableMapOf<String, String>() // convId → peerUserId
        conversations.values.forEach { conv ->
            if (conv.isPrivateChat()) {
                conv.members.find { it != userId }?.let {
                    peerUserIds.add(it)
                    peerUserIdMap[conv.id!!] = it
                }
            }
        }
        val peerUsers = userCacheService.getUsers(peerUserIds.toList())

        // 只查私聊对方的 UserConversation（用于 peerReadSeqId），不再拉全量
        val peerUcMap = if (peerUserIds.isNotEmpty()) {
            peerUserIdMap.mapNotNull { (convId, peerId) ->
                userConversationRepository.findFirstByUserIdAndConversationId(peerId, convId)
                    ?.let { convId to it }
            }.toMap()
        } else emptyMap()

        // Pipeline: batch read unread counts + serverMaxSeqIds (auto slot-grouped in Cluster)
        @Suppress("UNCHECKED_CAST")
        val ks = redisTemplate.keySerializer as RedisSerializer<String>
        val pipelineResults = try {
            redisTemplate.executePipelined { connection ->
                convIds.forEach { convId ->
                    connection.stringCommands().get(ks.serialize("$UNREAD_PREFIX$userId:$convId")!!)
                }
                convIds.forEach { convId ->
                    connection.stringCommands().get(ks.serialize("$SEQ_PREFIX$convId")!!)
                }
                null
            }
        } catch (e: Exception) {
            log.warn("[ConvList] Pipeline read failed: {}", e.message)
            emptyList()
        }
        val unreadMap = convIds.mapIndexed { i, convId ->
            convId to ((pipelineResults.getOrNull(i) as? String)?.toLongOrNull())
        }.toMap()
        val seqMap = convIds.mapIndexed { i, convId ->
            convId to ((pipelineResults.getOrNull(convIds.size + i) as? String)?.toLongOrNull())
        }.toMap()

        return conversations.values.mapNotNull { conv ->
            val uc = ucMap[conv.id] ?: return@mapNotNull null

            val unreadCount = unreadMap[conv.id] ?: 0L

            val peerUserId = peerUserIdMap[conv.id]
            val peerUser = peerUserId?.let { peerUsers[it] }
                ?: peerUserId?.let { userRepository.findById(it).orElse(null) }

            val peerReadSeqId = peerUcMap[conv.id]?.readSeqId ?: 0L

            ConversationDto(
                id = conv.id!!,
                type = conv.type,
                peerUserId = peerUserId,
                peerUserName = peerUser?.displayName,
                peerUserAvatar = peerUser?.avatarUrl,
                peerIsBot = peerUser?.isBot ?: false,
                lastMessageContent = normalizeContent(conv.lastMessageContent, conv.lastMessageContentType),
                lastMessageContentType = conv.lastMessageContentType,
                lastMessageSenderId = conv.lastMessageSenderId,
                lastMessageTime = conv.lastMessageTime ?: conv.createdAt,
                unreadCount = unreadCount,
                readSeqId = peerReadSeqId,
                pinned = uc.pinned,
                muted = uc.muted,
                createdAt = conv.createdAt,
                groupName = conv.name,
                groupAvatar = conv.avatarUrl,
                ownerId = conv.ownerId,
                memberCount = conv.members.size,
                muteAll = conv.muteAll,
                blockLinks = conv.blockLinks,
                announcement = conv.announcement,
                myNickname = uc.myNickname,
                groupRemark = uc.groupRemark,
                savedToContacts = uc.savedToContacts,
                mentionedSeqIds = uc.mentionedSeqIds,
                peerRemark = uc.peerRemark,
                joinMode = conv.joinMode,
                addFriendMode = GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
                searchable = conv.searchable,
                historyVisible = conv.historyVisible,
                adminIds = conv.adminIds,
                pinnedMessages = pinnedMessageDtos(conv),
                serverMaxSeqId = seqMap[conv.id],
                myDeviceReadSeqId = myDeviceReadMap[conv.id]
            )
        }.sortedByDescending { it.lastMessageTime ?: it.createdAt }
    }

    fun getConversationListPaginated(userId: String, limit: Int, beforeTimestamp: Long?, requesterDeviceId: String? = null): Pair<List<ConversationDto>, Boolean> {
        val all = getConversationList(userId, requesterDeviceId)
        val filtered = if (beforeTimestamp != null && beforeTimestamp > 0) {
            all.filter { (it.lastMessageTime ?: it.createdAt) < beforeTimestamp }
        } else {
            all
        }
        val page = filtered.take(limit)
        val hasMore = filtered.size > limit
        return page to hasMore
    }

    fun getConversationListAfter(userId: String, afterTimestamp: Long, requesterDeviceId: String? = null): List<ConversationDto> {
        // 優化：不再全量載入後記憶體過濾，改為先取會話 ID → 從快取篩選 → 只組裝符合條件的
        // 排除被用户软删除的会话
        val userConversations = getCachedUserConversations(userId).filter { !it.deleted }
        if (userConversations.isEmpty()) return emptyList()

        val convIds = userConversations.map { it.conversationId }
        val conversations = conversationCacheService.getConversations(convIds)
            .filter { (_, conv) -> (conv.lastMessageTime ?: 0L) >= afterTimestamp }

        if (conversations.isEmpty()) return emptyList()

        val filteredConvIds = conversations.keys.toList()
        val ucMap = userConversations.associateBy { it.conversationId }

        // Per-device readSeqId 批量查询（只针对过滤后的 convIds）
        val myDeviceReadMap: Map<String, Long> = if (!requesterDeviceId.isNullOrEmpty() && filteredConvIds.isNotEmpty()) {
            deviceReadPointRepository
                .findByUserIdAndDeviceIdAndConversationIdIn(userId, requesterDeviceId, filteredConvIds)
                .associate { it.conversationId to it.readSeqId }
        } else emptyMap()

        val peerUserIds = mutableSetOf<String>()
        val peerUserIdMap = mutableMapOf<String, String>()
        conversations.values.forEach { conv ->
            if (conv.isPrivateChat()) {
                conv.members.find { it != userId }?.let {
                    peerUserIds.add(it)
                    peerUserIdMap[conv.id!!] = it
                }
            }
        }
        val peerUsers = userCacheService.getUsers(peerUserIds.toList())

        val peerUcMap = if (peerUserIds.isNotEmpty()) {
            peerUserIdMap.mapNotNull { (convId, peerId) ->
                userConversationRepository.findFirstByUserIdAndConversationId(peerId, convId)
                    ?.let { convId to it }
            }.toMap()
        } else emptyMap()

        // Pipeline: batch read unread counts + serverMaxSeqIds
        @Suppress("UNCHECKED_CAST")
        val ks2 = redisTemplate.keySerializer as RedisSerializer<String>
        val pipelineResults2 = try {
            redisTemplate.executePipelined { connection ->
                filteredConvIds.forEach { convId ->
                    connection.stringCommands().get(ks2.serialize("$UNREAD_PREFIX$userId:$convId")!!)
                }
                filteredConvIds.forEach { convId ->
                    connection.stringCommands().get(ks2.serialize("$SEQ_PREFIX$convId")!!)
                }
                null
            }
        } catch (e: Exception) {
            log.warn("[ConvListAfter] Pipeline read failed: {}", e.message)
            emptyList()
        }
        val unreadMap = filteredConvIds.mapIndexed { i, convId ->
            convId to ((pipelineResults2.getOrNull(i) as? String)?.toLongOrNull())
        }.toMap()
        val seqMap = filteredConvIds.mapIndexed { i, convId ->
            convId to ((pipelineResults2.getOrNull(filteredConvIds.size + i) as? String)?.toLongOrNull())
        }.toMap()

        return conversations.values.mapNotNull { conv ->
            val uc = ucMap[conv.id] ?: return@mapNotNull null

            val unreadCount = unreadMap[conv.id] ?: 0L

            val peerUserId = peerUserIdMap[conv.id]
            val peerUser = peerUserId?.let { peerUsers[it] }
                ?: peerUserId?.let { userRepository.findById(it).orElse(null) }

            val peerReadSeqId = peerUcMap[conv.id]?.readSeqId ?: 0L

            ConversationDto(
                id = conv.id!!,
                type = conv.type,
                peerUserId = peerUserId,
                peerUserName = peerUser?.displayName,
                peerUserAvatar = peerUser?.avatarUrl,
                peerIsBot = peerUser?.isBot ?: false,
                lastMessageContent = normalizeContent(conv.lastMessageContent, conv.lastMessageContentType),
                lastMessageContentType = conv.lastMessageContentType,
                lastMessageSenderId = conv.lastMessageSenderId,
                lastMessageTime = conv.lastMessageTime ?: conv.createdAt,
                unreadCount = unreadCount,
                readSeqId = peerReadSeqId,
                pinned = uc.pinned,
                muted = uc.muted,
                createdAt = conv.createdAt,
                groupName = conv.name,
                groupAvatar = conv.avatarUrl,
                ownerId = conv.ownerId,
                memberCount = conv.members.size,
                muteAll = conv.muteAll,
                blockLinks = conv.blockLinks,
                announcement = conv.announcement,
                myNickname = uc.myNickname,
                groupRemark = uc.groupRemark,
                savedToContacts = uc.savedToContacts,
                mentionedSeqIds = uc.mentionedSeqIds,
                peerRemark = uc.peerRemark,
                joinMode = conv.joinMode,
                addFriendMode = GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
                searchable = conv.searchable,
                historyVisible = conv.historyVisible,
                adminIds = conv.adminIds,
                pinnedMessages = pinnedMessageDtos(conv),
                serverMaxSeqId = seqMap[conv.id],
                myDeviceReadSeqId = myDeviceReadMap[conv.id]
            )
        }.sortedByDescending { it.lastMessageTime ?: it.createdAt }
    }

    fun updateReadPoint(userId: String, conversationId: String, seqId: Long, deviceId: String? = null) {
        readPointFlushService.bufferReadPoint(userId, conversationId, seqId, deviceId)
        val key = "$UNREAD_PREFIX$userId:$conversationId"
        redisTemplate.opsForValue().set(key, "0", UNREAD_TTL)
        log.debug("Read point buffered for user {} device {} in conversation {} to seqId {}", userId, deviceId, conversationId, seqId)
    }

    fun incrementUnread(userId: String, conversationId: String) {
        val key = "$UNREAD_PREFIX$userId:$conversationId"
        redisTemplate.execute(INCR_WITH_TTL_SCRIPT, listOf(key), UNREAD_TTL.seconds.toString())
    }

    fun getUnreadCount(userId: String, conversationId: String): Long {
        return redisTemplate.opsForValue().get("$UNREAD_PREFIX$userId:$conversationId")?.toLongOrNull() ?: 0L
    }

    /**
     * Bug #18: 获取用户所有非免打扰会话的未读总数，用于 APNs 角标。
     * 优先从 Redis 读取缓存值，性能较好。
     */
    fun getTotalUnreadCount(userId: String): Long {
        val userConversations = getCachedUserConversations(userId)
        if (userConversations.isEmpty()) return 0L
        val nonMuted = userConversations.filter { !it.muted }
        if (nonMuted.isEmpty()) return 0L

        val keys = nonMuted.map { "$UNREAD_PREFIX$userId:${it.conversationId}" }
        @Suppress("UNCHECKED_CAST")
        val ks = redisTemplate.keySerializer as RedisSerializer<String>
        val results = try {
            redisTemplate.executePipelined { connection ->
                keys.forEach { key ->
                    connection.stringCommands().get(ks.serialize(key)!!)
                }
                null
            }
        } catch (e: Exception) {
            log.warn("[TotalUnread] Pipeline read failed: {}", e.message)
            return 0L
        }
        return results.sumOf { (it as? String)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L }
    }

    /**
     * 用户删除会话（仅删除该用户的会话记录，不影响对方）
     */
    fun deleteConversation(userId: String, conversationId: String) {
        userConversationRepository.deleteByUserIdAndConversationId(userId, conversationId)
        deviceReadPointRepository.deleteByUserIdAndConversationId(userId, conversationId)
        redisTemplate.delete("$UNREAD_PREFIX$userId:$conversationId")
        invalidateUserConversationCache(userId)
        log.info("User {} deleted conversation {}", userId, conversationId)
    }

    /**
     * 隐藏私聊会话历史的两种模式（"删除会话" / "清空聊天记录"）：
     *
     * - 仅在用户与对方"非好友"时，才在服务端写入水位线（hiddenBeforeSeqId = 当前最大 seqId）。
     *   一旦写入，无论换设备/重装登录，该用户都不能再拉回水位线之下的历史消息。
     * - 好友间删除/清空保持原有"纯本地"行为：后端不写库，客户端清本地后下一次拉历史仍可从云端漫游回来。
     * - "删除会话"额外把 UserConversation.deleted 置为 true，从会话列表中隐藏。
     * - 群聊不走这个分支，避免影响群聊语义。
     *
     * 重新加好友时会在 FriendService 把 hiddenBeforeSeqId 重置为 0、deleted 重置为 false，历史自动恢复。
     */
    data class HideConversationResult(
        val isFriend: Boolean,
        val hiddenBeforeSeqId: Long,
        val deleted: Boolean
    )

    fun deleteConversationForUser(userId: String, conversationId: String): HideConversationResult {
        return hideConversationForUser(userId, conversationId, alsoMarkDeleted = true)
    }

    fun clearHistoryForUser(userId: String, conversationId: String): HideConversationResult {
        return hideConversationForUser(userId, conversationId, alsoMarkDeleted = false)
    }

    private fun hideConversationForUser(
        userId: String,
        conversationId: String,
        alsoMarkDeleted: Boolean
    ): HideConversationResult {
        val conversation = conversationCacheService.getConversation(conversationId)
            ?: throw IllegalArgumentException("会话不存在")
        // 仅私聊（type=0）启用非好友水位线逻辑；群聊和特殊私聊保留原行为
        if (conversation.type != ConversationType.PRIVATE.value) {
            return HideConversationResult(isFriend = true, hiddenBeforeSeqId = 0L, deleted = false)
        }
        val peerId = conversation.members.find { it != userId }
            ?: return HideConversationResult(isFriend = true, hiddenBeforeSeqId = 0L, deleted = false)

        val isFriend = friendshipCacheService.isFriend(userId, peerId) &&
            friendshipCacheService.isFriend(peerId, userId)

        if (isFriend) {
            // 好友间删除/清空：保持原行为（纯客户端），后端不写水位线。
            return HideConversationResult(isFriend = true, hiddenBeforeSeqId = 0L, deleted = false)
        }

        // 非好友：取当前最大 seqId，所有 seqId <= watermark 的消息对该用户不可见
        val maxSeqId = messageRepository
            .findByConversationIdOrderBySeqIdDesc(conversationId, PageRequest.of(0, 1))
            .firstOrNull()?.seqId ?: 0L

        val existing = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId)
        val updated = if (existing != null) {
            existing.copy(
                hiddenBeforeSeqId = maxOf(existing.hiddenBeforeSeqId, maxSeqId),
                deleted = alsoMarkDeleted || existing.deleted
            )
        } else {
            // 极少数情况下 UserConversation 行未生成（早期数据/异常），补一条带水位线的
            UserConversation(
                userId = userId,
                conversationId = conversationId,
                hiddenBeforeSeqId = maxSeqId,
                deleted = alsoMarkDeleted,
                createdAt = System.currentTimeMillis()
            )
        }
        userConversationRepository.save(updated)
        invalidateUserConversationCache(userId)
        // 清掉该会话的未读缓存（避免列表里残留红点）
        try { redisTemplate.delete("$UNREAD_PREFIX$userId:$conversationId") } catch (_: Exception) {}

        log.info(
            "Conversation hidden for non-friend: user={} conv={} watermark={} deleted={}",
            userId, conversationId, updated.hiddenBeforeSeqId, updated.deleted
        )

        return HideConversationResult(
            isFriend = false,
            hiddenBeforeSeqId = updated.hiddenBeforeSeqId,
            deleted = updated.deleted
        )
    }

    /**
     * 加回好友时清除水位线，让历史消息重新可见。
     * 在 FriendService 建立双向 Friendship 后调用。
     */
    fun resetHiddenWatermark(userId: String, conversationId: String) {
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId) ?: return
        if (uc.hiddenBeforeSeqId == 0L && !uc.deleted) return
        userConversationRepository.save(uc.copy(hiddenBeforeSeqId = 0L, deleted = false))
        invalidateUserConversationCache(userId)
        log.info("Conversation watermark reset on re-friend: user={} conv={}", userId, conversationId)
    }

    fun getMembers(conversationId: String): List<String> {
        return conversationCacheService.getConversation(conversationId)?.members ?: emptyList()
    }

    fun getConversationType(conversationId: String): Int {
        return conversationCacheService.getConversation(conversationId)?.type ?: 0
    }

    fun setConversationPinned(userId: String, conversationId: String, pinned: Boolean) {
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId) ?: return
        if (uc.pinned == pinned) return
        userConversationRepository.save(uc.copy(pinned = pinned))
        invalidateUserConversationCache(userId)
        notifyConversationUpdated(userId, conversationId)
    }

    fun setConversationMuted(userId: String, conversationId: String, muted: Boolean) {
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId) ?: return
        if (uc.muted == muted) return
        userConversationRepository.save(uc.copy(muted = muted))
        invalidateUserConversationCache(userId)
        notifyConversationUpdated(userId, conversationId)
    }

    fun setConversationPeerRemark(userId: String, conversationId: String, remark: String?) {
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId) ?: return
        val trimmed = remark?.trim()?.ifEmpty { null }
        if (uc.peerRemark == trimmed) return
        userConversationRepository.save(uc.copy(peerRemark = trimmed))
        invalidateUserConversationCache(userId)
        notifyConversationUpdated(userId, conversationId)
    }

    private fun notifyConversationUpdated(userId: String, conversationId: String) {
        // Only query the single updated conversation instead of reloading the entire list
        val conv = conversationCacheService.getConversation(conversationId) ?: return
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId) ?: return
        val peerUserId = if (conv.isPrivateChat()) conv.members.find { it != userId } else null
        val peerUser = peerUserId?.let { userCacheService.getUser(it) }
            ?: peerUserId?.let { userRepository.findById(it).orElse(null) }
        val unreadCount = getUnreadCount(userId, conversationId)
        val serverMaxSeqId = getServerMaxSeqId(conversationId)

        val dto = ConversationDto(
            id = conv.id!!,
            type = conv.type,
            peerUserId = peerUserId,
            peerUserName = peerUser?.displayName,
            peerUserAvatar = peerUser?.avatarUrl,
            peerIsBot = peerUser?.isBot ?: false,
            lastMessageContent = normalizeContent(conv.lastMessageContent, conv.lastMessageContentType),
            lastMessageContentType = conv.lastMessageContentType,
            lastMessageSenderId = conv.lastMessageSenderId,
            lastMessageTime = conv.lastMessageTime ?: conv.createdAt,
            unreadCount = unreadCount,
            pinned = uc.pinned,
            muted = uc.muted,
            createdAt = conv.createdAt,
            groupName = conv.name,
            groupAvatar = conv.avatarUrl,
            ownerId = conv.ownerId,
            memberCount = conv.members.size,
            muteAll = conv.muteAll,
            blockLinks = conv.blockLinks,
            announcement = conv.announcement,
            myNickname = uc.myNickname,
            groupRemark = uc.groupRemark,
            savedToContacts = uc.savedToContacts,
            mentionedSeqIds = uc.mentionedSeqIds,
            peerRemark = uc.peerRemark,
            joinMode = conv.joinMode,
            addFriendMode = GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
            searchable = conv.searchable,
            historyVisible = conv.historyVisible,
            adminIds = conv.adminIds,
            pinnedMessages = pinnedMessageDtos(conv),
            serverMaxSeqId = serverMaxSeqId
        )
        val payload = objectMapper.writeValueAsString(
            mapOf("type" to "conversation_updated", "data" to dto)
        )
        userSessionManager.pushToUser(userId, payload)
    }

    /**
     * 发送者在本端已收到 message_sent_ack；向其它在线会话补发 conversation_updated，
     * 且最后一条消息相关字段以本次下发的 [messageDto] 为准，避免依赖会话缓存/异步落库的旧值。
     */
    fun pushConversationUpdatedForSenderOtherSessions(
        senderId: String,
        conversationId: String,
        messageDto: MessageDto,
        excludeChannel: Channel?
    ) {
        try {
            val conv = conversationCacheService.getConversation(conversationId) ?: return
            val uc = userConversationRepository.findFirstByUserIdAndConversationId(senderId, conversationId) ?: return
            val peerUserId = if (conv.isPrivateChat()) conv.members.find { it != senderId } else null
            val peerUser = peerUserId?.let { userCacheService.getUser(it) }
                ?: peerUserId?.let { userRepository.findById(it).orElse(null) }
            val unreadCount = getUnreadCount(senderId, conversationId)
            val serverMaxSeqId = getServerMaxSeqId(conversationId)
            val lastSenderName =
                if (conv.type == ConversationType.GROUP.value) messageDto.senderName else null

            val dto = ConversationDto(
                id = conv.id!!,
                type = conv.type,
                peerUserId = peerUserId,
                peerUserName = peerUser?.displayName,
                peerUserAvatar = peerUser?.avatarUrl,
                peerIsBot = peerUser?.isBot ?: false,
                lastMessageContent = normalizeContent(messageDto.content, messageDto.contentType),
                lastMessageContentType = messageDto.contentType,
                lastMessageSenderId = messageDto.senderId,
                lastMessageTime = messageDto.createdAt,
                lastMessageSenderName = lastSenderName,
                unreadCount = unreadCount,
                pinned = uc.pinned,
                muted = uc.muted,
                createdAt = conv.createdAt,
                groupName = conv.name,
                groupAvatar = conv.avatarUrl,
                ownerId = conv.ownerId,
                memberCount = conv.members.size,
                muteAll = conv.muteAll,
                blockLinks = conv.blockLinks,
                announcement = conv.announcement,
                myNickname = uc.myNickname,
                groupRemark = uc.groupRemark,
                savedToContacts = uc.savedToContacts,
                mentionedSeqIds = uc.mentionedSeqIds,
                peerRemark = uc.peerRemark,
                joinMode = conv.joinMode,
                addFriendMode = GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
                searchable = conv.searchable,
                historyVisible = conv.historyVisible,
                adminIds = conv.adminIds,
                pinnedMessages = pinnedMessageDtos(conv),
                serverMaxSeqId = serverMaxSeqId
            )
            val payload = objectMapper.writeValueAsString(
                mapOf("type" to "conversation_updated", "data" to dto)
            )
            if (excludeChannel != null) {
                userSessionManager.pushToUserExcluding(senderId, payload, excludeChannel)
            } else {
                userSessionManager.pushToUser(senderId, payload)
            }
        } catch (e: Exception) {
            log.warn(
                "Failed to push conversation_updated (sender other sessions) for conv={} user={}",
                conversationId, senderId, e
            )
        }
    }

    /**
     * 向消息接收者（单人或多人）推送 conversation_updated，让客户端会话列表实时刷新
     * lastMessage / 未读 / 时间戳。
     *
     * 批量 UC 查询 + Redis Pipeline 读未读，避免群聊 N 次 Mongo/Redis 访问。
     * APNs 推送由上游 new_message 已触发，此处显式 skipApns=true 避免重复推送。
     */
    fun pushConversationUpdatedForRecipients(
        recipientIds: List<String>,
        conversationId: String,
        lastMessageContent: String,
        lastMessageContentType: Int,
        lastMessageSenderId: String,
        lastMessageTime: Long,
        lastMessageSenderName: String?
    ) {
        if (recipientIds.isEmpty()) return
        val conv = conversationCacheService.getConversation(conversationId) ?: return
        val recipientSet = recipientIds.toSet()
        val ucMap = userConversationRepository.findByConversationId(conversationId)
            .asSequence()
            .filter { it.userId in recipientSet }
            .associateBy { it.userId }
        val unreadMap = batchReadUnreadForUsers(recipientIds, conversationId)
        val serverMaxSeqId = getServerMaxSeqId(conversationId)

        val peerUserIds = if (conv.isPrivateChat()) {
            recipientIds.mapNotNull { r -> conv.members.find { it != r } }.toSet()
        } else emptySet()
        val peerUsers = if (peerUserIds.isNotEmpty())
            userCacheService.getUsers(peerUserIds.toList()) else emptyMap()

        val groupLastSenderName =
            if (conv.type == ConversationType.GROUP.value) lastMessageSenderName else null

        recipientIds.forEach { recipientId ->
            val uc = ucMap[recipientId] ?: return@forEach
            val peerUserId = if (conv.isPrivateChat()) conv.members.find { it != recipientId } else null
            val peerUser = peerUserId?.let { peerUsers[it] }
            val unreadCount = unreadMap[recipientId] ?: 0L

            val dto = ConversationDto(
                id = conv.id!!,
                type = conv.type,
                peerUserId = peerUserId,
                peerUserName = peerUser?.displayName,
                peerUserAvatar = peerUser?.avatarUrl,
                peerIsBot = peerUser?.isBot ?: false,
                lastMessageContent = normalizeContent(lastMessageContent, lastMessageContentType),
                lastMessageContentType = lastMessageContentType,
                lastMessageSenderId = lastMessageSenderId,
                lastMessageSenderName = groupLastSenderName,
                lastMessageTime = lastMessageTime,
                unreadCount = unreadCount,
                pinned = uc.pinned,
                muted = uc.muted,
                createdAt = conv.createdAt,
                groupName = conv.name,
                groupAvatar = conv.avatarUrl,
                ownerId = conv.ownerId,
                memberCount = conv.members.size,
                muteAll = conv.muteAll,
                blockLinks = conv.blockLinks,
                announcement = conv.announcement,
                myNickname = uc.myNickname,
                groupRemark = uc.groupRemark,
                savedToContacts = uc.savedToContacts,
                mentionedSeqIds = uc.mentionedSeqIds,
                peerRemark = uc.peerRemark,
                joinMode = conv.joinMode,
                addFriendMode = GroupSettingsService.normalizeAddFriendMode(conv.addFriendMode),
                searchable = conv.searchable,
                historyVisible = conv.historyVisible,
                adminIds = conv.adminIds,
                pinnedMessages = pinnedMessageDtos(conv),
                serverMaxSeqId = serverMaxSeqId
            )
            val payload = objectMapper.writeValueAsString(
                mapOf("type" to "conversation_updated", "data" to dto)
            )
            userSessionManager.pushToUser(
                recipientId, payload, skipApns = true, messageType = "conversation_updated"
            )
        }
    }

    private fun batchReadUnreadForUsers(userIds: List<String>, convId: String): Map<String, Long> {
        if (userIds.isEmpty()) return emptyMap()
        val keys = userIds.map { "$UNREAD_PREFIX$it:$convId" }
        val results = try {
            redisTemplate.executePipelined { connection ->
                keys.forEach { k -> connection.stringCommands().get(k.toByteArray()) }
                null
            }
        } catch (e: Exception) {
            log.warn("Pipeline read unread for users failed: {}", e.message)
            return emptyMap()
        }
        return userIds.mapIndexedNotNull { index, uid ->
            (results.getOrNull(index) as? String)?.toLongOrNull()?.let { uid to it }
        }.toMap()
    }

    private fun normalizeContent(content: String?, contentType: Int): String? {
        return MediaContentUrlNormalizer.normalizeNullable(
            content,
            contentType,
            objectMapper,
            videoCompatPublicBase = proxyPublicBase,
        )
    }

    private fun pinnedMessageDtos(conv: com.layababateam.xinxiwang_backend.model.Conversation): List<PinnedMessageDto> {
        return conv.pinnedMessages.map {
            PinnedMessageDto(
                messageId = it.messageId,
                content = normalizeContent(it.content, it.contentType),
                contentType = it.contentType,
                senderName = it.senderName,
                seqId = it.seqId,
                pinnedBy = it.pinnedBy,
                pinnedAt = it.pinnedAt,
            )
        }
    }

    /**
     * 从 Redis 读取会话的当前最大 seqId。
     * key 不存在时返回 null（不回查 Mongo，避免慢查询）。
     */
    private fun getServerMaxSeqId(conversationId: String): Long? {
        return try {
            redisTemplate.opsForValue().get("$SEQ_PREFIX$conversationId")?.toLongOrNull()
        } catch (e: Exception) {
            log.debug("[SeqId] Failed to read serverMaxSeqId for conv={}: {}", conversationId, e.message)
            null
        }
    }

    /**
     * 获取单个会话的 ConversationDto，供 get_conversation_info handler 使用。
     * 如果会话不存在或用户不属于该会话，返回 null。
     */
    override fun getConversationInfo(
        userId: String,
        conversationId: String,
        requesterDeviceId: String?,
    ): ConversationDto? = getConversationDto(userId, conversationId, requesterDeviceId)

    fun getConversationDto(userId: String, conversationId: String, requesterDeviceId: String? = null): ConversationDto? {
        val conv = conversationCacheService.getConversation(conversationId) ?: return null
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId)
            ?: return null

        val peerUserId = if (conv.isPrivateChat()) conv.members.find { it != userId } else null
        val peerUser = peerUserId?.let { userCacheService.getUser(it) }
            ?: peerUserId?.let { userRepository.findById(it).orElse(null) }

        val unreadCount = getUnreadCount(userId, conversationId)
        val serverMaxSeqId = getServerMaxSeqId(conversationId)

        val peerReadSeqId = if (peerUserId != null) {
            userConversationRepository.findFirstByUserIdAndConversationId(peerUserId, conversationId)
                ?.readSeqId ?: 0L
        } else 0L

        // Per-device readSeqId：单次查询走复合唯一索引 O(log N)
        val myDeviceReadSeqId: Long? = if (!requesterDeviceId.isNullOrEmpty()) {
            deviceReadPointRepository
                .findFirstByUserIdAndDeviceIdAndConversationId(userId, requesterDeviceId, conversationId)
                ?.readSeqId
        } else null

        return ConversationDto(
            id = conv.id!!,
            type = conv.type,
            peerUserId = peerUserId,
            peerUserName = peerUser?.displayName,
            peerUserAvatar = peerUser?.avatarUrl,
            peerIsBot = peerUser?.isBot ?: false,
            lastMessageContent = normalizeContent(conv.lastMessageContent, conv.lastMessageContentType),
            lastMessageContentType = conv.lastMessageContentType,
            lastMessageSenderId = conv.lastMessageSenderId,
            lastMessageTime = conv.lastMessageTime ?: conv.createdAt,
            unreadCount = unreadCount,
            readSeqId = peerReadSeqId,
            pinned = uc.pinned,
            muted = uc.muted,
            createdAt = conv.createdAt,
            groupName = conv.name,
            groupAvatar = conv.avatarUrl,
            ownerId = conv.ownerId,
            memberCount = conv.members.size,
            muteAll = conv.muteAll,
            blockLinks = conv.blockLinks,
            announcement = conv.announcement,
            myNickname = uc.myNickname,
            groupRemark = uc.groupRemark,
            savedToContacts = uc.savedToContacts,
            mentionedSeqIds = uc.mentionedSeqIds,
            peerRemark = uc.peerRemark,
            joinMode = conv.joinMode,
            addFriendMode = conv.addFriendMode,
            searchable = conv.searchable,
            historyVisible = conv.historyVisible,
            adminIds = conv.adminIds,
            pinnedMessages = pinnedMessageDtos(conv),
            serverMaxSeqId = serverMaxSeqId,
            myDeviceReadSeqId = myDeviceReadSeqId
        )
    }
}
