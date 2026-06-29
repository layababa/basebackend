package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.dto.MessageDto
import com.layababateam.xinxiwang_backend.dto.ReplyToDto
import com.layababateam.xinxiwang_backend.model.ContentType
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.ConversationType
import com.layababateam.xinxiwang_backend.model.Message
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.FriendshipRepository
import com.layababateam.xinxiwang_backend.repository.MessageRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import io.netty.channel.Channel
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Service
import kotlin.random.Random

data class PagedSyncResult(
    val messages: List<MessageDto>,
    val hasMore: Boolean,
    val nextAfterSeqId: Long
)

internal fun normalizeOutgoingContent(
    content: String,
    contentType: Int,
    objectMapper: ObjectMapper,
    diceImageBaseUrl: String? = null,
    mediaCompatPublicBase: String? = null,
    rollDice: () -> Int = { Random.nextInt(1, 7) }
): String {
    val mediaNormalizedContent = MediaContentUrlNormalizer.normalize(
        content,
        contentType,
        objectMapper,
        videoCompatPublicBase = mediaCompatPublicBase,
    )
    if (contentType != ContentType.STICKER.value) return mediaNormalizedContent
    val payload = try {
        objectMapper.readValue(mediaNormalizedContent, Map::class.java)
    } catch (_: Exception) {
        return mediaNormalizedContent
    }
    if (payload["type"] != "dice") return mediaNormalizedContent
    val value = rollDice().coerceIn(1, 6)
    val normalized = mutableMapOf<String, Any>(
        "type" to "dice",
        "value" to value
    )
    diceImageBaseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let {
        normalized["url"] = "$it/$value.png"
    }
    return objectMapper.writeValueAsString(normalized)
}

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val userConversationRepository: UserConversationRepository,
    private val userSessionManager: UserSessionManager,
    private val ackRetryService: AckRetryService,
    private val rabbitPublishService: RabbitPublishService,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val userCacheService: UserCacheService,
    private val conversationCacheService: ConversationCacheService,
    private val friendshipCacheService: com.layababateam.xinxiwang_backend.service.cache.FriendshipCacheService,
    private val messageBatchService: MessageBatchService,
    private val conversationService: ConversationService,
    private val distributedLockService: DistributedLockService,
    private val historicalEncryptedMediaFallbackService: HistoricalEncryptedMediaFallbackService,
    private val mongoTemplate: MongoTemplate,
    @Value("\${rentmsg.media.proxy.public-base}") private val proxyPublicBase: String
) : GroupSystemMessagePort {
    private val log = LoggerFactory.getLogger(MessageService::class.java)

    private fun normalizeAndRepairOutgoingContent(content: String, contentType: Int): String {
        val normalized = MediaContentUrlNormalizer.normalize(
            content,
            contentType,
            objectMapper,
            videoCompatPublicBase = proxyPublicBase,
        )
        return historicalEncryptedMediaFallbackService.apply(normalized, contentType, content)
    }

    fun getMessageById(messageId: String): Message? {
        return messageRepository.findById(messageId).orElse(null)
    }

    private fun persistMessageDirectly(
        messageId: String,
        conversationId: String,
        senderId: String,
        contentType: Int,
        content: String,
        seqId: Long,
        mentions: List<String>,
        replyToMessageId: String?,
        createdAt: Long,
        isGroupChat: Boolean
    ) {
        val message = messageRepository.save(
            Message(
                id = messageId,
                conversationId = conversationId,
                senderId = senderId,
                contentType = contentType,
                content = content,
                seqId = seqId,
                mentions = mentions,
                replyToMessageId = replyToMessageId,
                createdAt = createdAt
            )
        )

        val updateResult = mongoTemplate.updateFirst(
            Query(
                Criteria.where("_id").`is`(conversationId).andOperator(
                    Criteria().orOperator(
                        Criteria.where("lastMessageTime").lt(createdAt),
                        Criteria.where("lastMessageTime").isNull
                    )
                )
            ),
            Update()
                .set("lastMessageId", message.id)
                .set("lastMessageContent", content)
                .set("lastMessageContentType", contentType)
                .set("lastMessageSenderId", senderId)
                .set("lastMessageTime", createdAt),
            Conversation::class.java
        )
        conversationCacheService.invalidate(conversationId)
        if (updateResult.modifiedCount == 0L) {
            log.debug("Skipped direct conversation update for conv={} (newer message already persisted)", conversationId)
        }

        if (isGroupChat) {
            val uc = userConversationRepository.findFirstByUserIdAndConversationId(senderId, conversationId)
            if (uc != null) {
                userConversationRepository.save(uc.copy(lastActiveAt = createdAt))
            }

            if (mentions.isNotEmpty()) {
                val convMembers = conversationRepository.findById(conversationId)
                    .map { it.members }.orElse(emptyList())
                val mentionedUserIds = if (mentions.contains("all")) {
                    convMembers.filter { it != senderId }
                } else {
                    mentions.filter { it in convMembers && it != senderId }
                }
                mentionedUserIds.forEach { uid ->
                    val muc = userConversationRepository.findFirstByUserIdAndConversationId(uid, conversationId)
                    if (muc != null) {
                        userConversationRepository.save(muc.copy(mentionedSeqIds = muc.mentionedSeqIds + seqId))
                    }
                }
            }
        }
    }

    fun sendMessage(
        senderId: String,
        conversationId: String,
        content: String,
        contentType: Int = 0,
        mentions: List<String> = emptyList(),
        clientMessageId: String? = null,
        replyToMessageId: String? = null,
        isBotSender: Boolean = false,
        sourceChannel: Channel? = null
    ): Message {
        val normalizedContent = normalizeOutgoingContent(
            content,
            contentType,
            objectMapper,
            diceImageBaseUrl = "${proxyPublicBase.trimEnd('/')}/api/public/dice",
            mediaCompatPublicBase = proxyPublicBase,
        )
        val conversation = conversationCacheService.getConversation(conversationId)
            ?: throw IllegalArgumentException("会话不存在")

        // ── 私聊校验：检查对方是否已删除/拉黑发送者 (type=0) ──
        // Bot 发送者跳过好友关系检查
        if (conversation.type == ConversationType.PRIVATE.value && !isBotSender) {
            val peerId = conversation.members.find { it != senderId }
            if (peerId != null) {
                if (!friendshipCacheService.isFriend(peerId, senderId)) {
                    throw IllegalStateException("对方已将你删除")
                }
                val peerFriendship = friendshipRepository.findByUserIdAndFriendId(peerId, senderId)
                if (peerFriendship?.blockedAt != null) {
                    throw IllegalStateException("你已被对方拉黑")
                }
            }
        }

        // ── 群聊校验链 (type=1) ──
        if (conversation.type == ConversationType.GROUP.value) {
            require(senderId in conversation.members) { "您不在此群" }
            val isAdmin = senderId == conversation.ownerId || senderId in conversation.adminIds
            if (conversation.muteAll && !isAdmin) throw IllegalStateException("全员禁言中")
            if (senderId in conversation.mutedMembers) throw IllegalStateException("您已被禁言")
            if (conversation.blockLinks && contentType == ContentType.TEXT.value && URL_REGEX.containsMatchIn(content)) {
                throw IllegalStateException("本群禁止发送链接")
            }
        }

        // ── @all 权限过滤：非群主/管理员静默移除 "all" ──
        val filteredMentions = if (conversation.type == ConversationType.GROUP.value
            && mentions.contains("all")
            && senderId != conversation.ownerId
            && senderId !in conversation.adminIds
        ) {
            mentions.filter { it != "all" }
        } else {
            mentions
        }

        // 1. 分配 SeqID（持久化校准 + 分布式锁，P0-S6）
        val seqId = allocateSeqId(conversationId)
        val now = System.currentTimeMillis()
        val messageId = ObjectId().toHexString()

        // 2. Critical path persistence must not depend on RabbitMQ availability.
        // Rabbit is still used for fanout/background work, but MongoDB is the source of truth.
        persistMessageDirectly(
            messageId = messageId,
            conversationId = conversationId,
            senderId = senderId,
            contentType = contentType,
            content = normalizedContent,
            seqId = seqId,
            mentions = filteredMentions,
            replyToMessageId = replyToMessageId,
            createdAt = now,
            isGroupChat = conversation.type == ConversationType.GROUP.value
        )

        // 3. Build ReplyToDto if replying
        val replyToDto = if (replyToMessageId != null) {
            val origMsg = messageRepository.findById(replyToMessageId).orElse(null)
            if (origMsg != null) {
                if (origMsg.isRecalled) {
                    ReplyToDto(
                        messageId = origMsg.id!!,
                        senderId = origMsg.senderId,
                        content = "[消息已撤回]",
                        contentType = 0,
                        isRecalled = true
                    )
                } else {
                    val origSender = userCacheService.getUser(origMsg.senderId)
                    val origNickname = if (conversation.type == ConversationType.GROUP.value) {
                        userConversationRepository.findFirstByUserIdAndConversationId(origMsg.senderId, conversationId)?.myNickname
                    } else null
                    ReplyToDto(
                        messageId = origMsg.id!!,
                        senderId = origMsg.senderId,
                        senderName = origNickname ?: origSender?.displayName,
                        content = normalizeAndRepairOutgoingContent(origMsg.content, origMsg.contentType),
                        contentType = origMsg.contentType
                    )
                }
            } else null
        } else null
        // 4. 构造推送 DTO（无需等待落盘完成）
        val sender = userCacheService.getUser(senderId)
        val senderNickname = if (conversation.type == ConversationType.GROUP.value) {
            userConversationRepository.findFirstByUserIdAndConversationId(senderId, conversationId)?.myNickname
        } else null
        val senderRole = when {
            conversation.type != ConversationType.GROUP.value -> 0
            senderId == conversation.ownerId -> 2
            senderId in conversation.adminIds -> 1
            else -> 0
        }

        val messageDto = MessageDto(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderNickname ?: sender?.displayName,
            senderAvatar = sender?.avatarUrl,
            contentType = contentType,
            content = normalizedContent,
            seqId = seqId,
            mentions = filteredMentions,
            createdAt = now,
            clientMessageId = clientMessageId,
            replyTo = replyToDto,
            senderRole = senderRole,
            senderIsBot = sender?.isBot ?: false,
            groupName = if (conversation.type == ConversationType.GROUP.value) conversation.name else null,
            groupAvatar = if (conversation.type == ConversationType.GROUP.value) conversation.avatarUrl else null
        )

        // Sender ACK means "server accepted the message". Do not wait for receiver fanout,
        // unread counters, or conversation_updated pushes; those have sync/retry fallback.
        userSessionManager.pushToUser(senderId, objectMapper.writeValueAsString(
            mapOf("type" to "message_sent_ack", "data" to messageDto)
        ), messageType = "message_sent_ack")

        // 4. 即时推送给接收者 + QoS 待确认队列
        val receiverIds = conversation.members.filter { it != senderId }

        if (conversation.type == ConversationType.GROUP.value) {
            // 群聊：payload 只序列化一次，投递到 MQ 异步分发
            val groupPayload = objectMapper.writeValueAsString(mapOf("type" to "new_message", "data" to messageDto))
            rabbitPublishService.send(
                RabbitMQConfig.GROUP_MESSAGE_DISPATCH_EXCHANGE,
                RabbitMQConfig.groupMessageDispatchRoutingKeyForConversation(conversationId),
                mapOf(
                    "conversationId" to conversationId,
                    "senderId" to senderId,
                    "messagePayload" to groupPayload,
                    "seqId" to seqId,
                    "memberIds" to receiverIds,
                    // 供消费端向成员补推 conversation_updated
                    "lastMessageContent" to normalizedContent,
                    "lastMessageContentType" to contentType,
                    "lastMessageTime" to now,
                    "lastMessageSenderName" to messageDto.senderName
                ),
                "group_message_dispatch conv=$conversationId seqId=$seqId",
            )
        } else {
            // 私聊：直推（仅 1 人），支持备注名个性化
            val remarkMap = if (receiverIds.isNotEmpty()) {
                friendshipRepository.findByFriendIdAndUserIdIn(senderId, receiverIds)
                    .associate { it.userId to it.remark }
            } else emptyMap()

            receiverIds.forEach { memberId ->
                val remark = remarkMap[memberId]?.takeIf { it.isNotBlank() }
                val personalizedDto = if (remark != null) messageDto.copy(senderName = remark) else messageDto
                val personalizedPayload = objectMapper.writeValueAsString(mapOf("type" to "new_message", "data" to personalizedDto))
                userSessionManager.pushToUser(memberId, personalizedPayload, messageType = "new_message")
                ackRetryService.addPendingAck(memberId, seqId, conversationId, personalizedPayload)
                // Increment unread count (delegates to Lua atomic INCR+EXPIRE)
                conversationService.incrementUnread(memberId, conversationId)
            }

            if (receiverIds.isNotEmpty()) {
                try {
                    conversationService.pushConversationUpdatedForRecipients(
                        recipientIds = receiverIds,
                        conversationId = conversationId,
                        lastMessageContent = normalizedContent,
                        lastMessageContentType = contentType,
                        lastMessageSenderId = senderId,
                        lastMessageTime = now,
                        lastMessageSenderName = messageDto.senderName
                    )
                } catch (e: Exception) {
                    log.warn(
                        "Failed to push conversation_updated to recipients: conv={} {}",
                        conversationId,
                        e.message
                    )
                }
            }
        }
        conversationService.pushConversationUpdatedForSenderOtherSessions(
            senderId, conversationId, messageDto, sourceChannel
        )

        log.info("Message sent in conversation {} by user {}, seqId={}", conversationId, senderId, seqId)
        return Message(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            contentType = contentType,
            content = normalizedContent,
            seqId = seqId,
            mentions = filteredMentions,
            replyToMessageId = replyToMessageId,
            createdAt = now
        )
    }

    override fun sendGroupSystemMessage(
        senderId: String,
        conversationId: String,
        content: String,
        contentType: Int,
    ) {
        sendMessage(
            senderId = senderId,
            conversationId = conversationId,
            content = content,
            contentType = contentType,
        )
    }

    fun getHistory(userId: String, conversationId: String, beforeSeqId: Long?, limit: Int = 30): List<MessageDto> {
        var cursor = beforeSeqId ?: Long.MAX_VALUE
        val result = mutableListOf<Message>()
        val fetchSize = limit * 2
        val maxRounds = 3 // 最多补拉 3 轮，防止极端情况下无限循环

        repeat(maxRounds) {
            if (result.size >= limit) return@repeat
            val batch = messageRepository.findByConversationIdAndSeqIdLessThanOrderBySeqIdDesc(
                conversationId, cursor, PageRequest.of(0, fetchSize)
            )
            if (batch.isEmpty()) return@repeat

            val filtered = filterByHistoryVisibility(userId, conversationId,
                applyUserVisibility(userId, conversationId, batch)
            )
            result.addAll(filtered)
            cursor = batch.last().seqId
        }

        return toDtos(result.take(limit)).reversed()
    }

    fun getLatestMessages(userId: String, conversationId: String, limit: Int = 30): List<MessageDto> {
        val messages = messageRepository.findByConversationIdOrderBySeqIdDesc(
            conversationId, PageRequest.of(0, limit)
        )
        val filtered = applyUserVisibility(userId, conversationId, messages)
        return toDtos(filtered).reversed()
    }

    /**
     * 首屏最新 N 条（用于"长离线进群只显示最新 K 条"的新策略）：
     * - 与 getLatestMessages 的区别：必须套 filterByHistoryVisibility，
     *   避免 historyVisible=false 的群泄露用户入群前的消息。
     * - 返回按 seqId 升序，客户端直接按顺序渲染。
     */
    fun getRecentHistoryForUser(userId: String, conversationId: String, limit: Int): List<MessageDto> {
        val raw = messageRepository.findByConversationIdOrderBySeqIdDesc(
            conversationId, PageRequest.of(0, limit)
        )
        val afterDeleted = applyUserVisibility(userId, conversationId, raw)
        val visible = filterByHistoryVisibility(userId, conversationId, afterDeleted)
        return toDtos(visible).reversed()
    }

    fun batchGetHistory(userId: String, conversationIds: List<String>, limit: Int = 30): Map<String, List<MessageDto>> {
        if (conversationIds.isEmpty()) return emptyMap()
        // 一次性查询所有 UserConversation 水位线，避免 N+1
        val watermarks = userConversationRepository
            .findByConversationIdInAndUserIdIn(conversationIds, listOf(userId))
            .associate { it.conversationId to it.hiddenBeforeSeqId }
        // 批量查詢所有對話的最新訊息，避免 N+1
        val allMessages = conversationIds.flatMap { convId ->
            messageRepository.findByConversationIdOrderBySeqIdDesc(convId, PageRequest.of(0, limit))
        }
        val filtered = allMessages.filter {
            !it.deletedBy.contains(userId) && it.seqId > (watermarks[it.conversationId] ?: 0L)
        }
        val dtos = toDtos(filtered)
        return dtos.groupBy { it.conversationId }
    }

    fun syncMessages(userId: String, conversationId: String, afterSeqId: Long): List<MessageDto> {
        val messages = messageRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
            conversationId, afterSeqId, PageRequest.of(0, 50)
        )
        // 埋點 B1：sync 命中 50 條上限（疑似上限截斷，客戶端可能漏訊息）
        if (messages.size >= 50) {
            com.layababateam.xinxiwang_backend.extensions.SentryReporter.captureSampled(
                dedupKey = "im_sync_cap:$conversationId",
                message = "[IM_SYNC] sync_response hit cap (50)",
                level = io.sentry.SentryLevel.WARNING,
                tags = mapOf("im_event" to "sync_cap_hit"),
                extras = mapOf(
                    "userId" to userId,
                    "conversationId" to conversationId,
                    "afterSeqId" to afterSeqId,
                    "returnedSize" to messages.size
                )
            )
        }
        var filtered = applyUserVisibility(userId, conversationId, messages)
        filtered = filterByHistoryVisibility(userId, conversationId, filtered)
        return toDtos(filtered)
    }

    // ─── v3 API ──────────────────────────────────────────────────────────────

    fun syncMessagesPaginated(userId: String, conversationId: String, afterSeqId: Long, limit: Int): List<MessageDto> {
        val messages = messageRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
            conversationId, afterSeqId, org.springframework.data.domain.PageRequest.of(0, limit)
        )
        var filtered = applyUserVisibility(userId, conversationId, messages)
        filtered = filterByHistoryVisibility(userId, conversationId, filtered)
        return toDtos(filtered)
    }

    fun queryMessagesV3(
        userId: String,
        conversationId: String,
        afterSeqId: Long? = null,
        beforeSeqId: Long? = null,
        deliveryDateStart: Long? = null,
        deliveryDateEnd: Long? = null,
        maxCount: Int = 51,
        descending: Boolean = true,
        contentTypes: List<Int>? = null,
    ): List<MessageDto> {
        val messages = when {
            afterSeqId != null -> messageRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
                conversationId, afterSeqId, org.springframework.data.domain.PageRequest.of(0, maxCount)
            )
            beforeSeqId != null -> messageRepository.findByConversationIdAndSeqIdLessThanOrderBySeqIdDesc(
                conversationId, beforeSeqId, org.springframework.data.domain.PageRequest.of(0, maxCount)
            )
            else -> messageRepository.findByConversationIdOrderBySeqIdDesc(
                conversationId, org.springframework.data.domain.PageRequest.of(0, maxCount)
            )
        }
        var filtered = applyUserVisibility(userId, conversationId, messages)
        if (deliveryDateStart != null) {
            filtered = filtered.filter { it.createdAt >= deliveryDateStart }
        }
        if (deliveryDateEnd != null) {
            filtered = filtered.filter { it.createdAt <= deliveryDateEnd }
        }
        if (!contentTypes.isNullOrEmpty()) {
            filtered = filtered.filter { contentTypes.contains(it.contentType) }
        }
        filtered = filterByHistoryVisibility(userId, conversationId, filtered)
        return toDtos(filtered)
    }

    fun countMessages(userId: String, conversationId: String): Long {
        return messageRepository.countByConversationIdAndSenderIdNotAndCreatedAtGreaterThan(
            conversationId, userId, 0L
        )
    }

    /**
     * 分页增量同步：查 limit+1 条判断 hasMore，返回 PagedSyncResult。
     * 关键：nextAfterSeqId 基于 raw（过滤前）的最后一条 seqId 推进游标，
     * 避免 filterByHistoryVisibility 全过滤后客户端死循环。
     */
    fun syncMessagesPaged(userId: String, conversationId: String, afterSeqId: Long, limit: Int): PagedSyncResult {
        val raw = messageRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
            conversationId, afterSeqId, PageRequest.of(0, limit + 1)
        )
        val hasMore = raw.size > limit
        val trimmed = if (hasMore) raw.take(limit) else raw
        var filtered = applyUserVisibility(userId, conversationId, trimmed)
        filtered = filterByHistoryVisibility(userId, conversationId, filtered)
        val dtos = toDtos(filtered)
        // 用 raw 的最后一条 seqId 推进游标，即便 filtered 为空也能推进
        val nextAfterSeqId = if (trimmed.isNotEmpty()) trimmed.last().seqId else afterSeqId
        return PagedSyncResult(dtos, hasMore, nextAfterSeqId)
    }

    /**
     * 用户级可见性过滤：
     *  - 排除被该用户标记为已删除（Message.deletedBy）的消息
     *  - 排除位于该用户隐藏水位线之下的消息（UserConversation.hiddenBeforeSeqId）
     *
     * 水位线由"非好友状态删除会话/清空记录"写入，加回好友后会被重置为 0，恢复可见。
     */
    private fun applyUserVisibility(userId: String, conversationId: String, messages: List<Message>): List<Message> {
        if (messages.isEmpty()) return messages
        val watermark = userConversationRepository
            .findFirstByUserIdAndConversationId(userId, conversationId)
            ?.hiddenBeforeSeqId ?: 0L
        return messages.filter { !it.deletedBy.contains(userId) && it.seqId > watermark }
    }

    /**
     * 群聊历史消息可见性过滤：当 historyVisible=false 时，
     * 只返回用户加入群聊之后的消息（createdAt >= joinedAt）。
     * historyVisible=true 或非群聊时不做过滤。
     */
    private fun filterByHistoryVisibility(userId: String, conversationId: String, messages: List<Message>): List<Message> {
        if (messages.isEmpty()) return messages
        val conversation = conversationCacheService.getConversation(conversationId) ?: return messages
        if (conversation.type != ConversationType.GROUP.value) return messages
        if (conversation.historyVisible) return messages
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, conversationId)
            ?: return messages
        val joinedAt = uc.createdAt
        return messages.filter { it.createdAt >= joinedAt }
    }

    companion object {
        private const val MAX_CALIBRATED_CACHE_SIZE = 100_000
        /**
         * JVM 本地缓存：已校准 seqId 的会话集合。
         * 仅作为性能优化，避免每次发消息都查 MongoDB；
         * 真正的持久化标记存储在 Conversation.seqCalibrated 字段。
         * 进程重启后清空是安全的——会从 MongoDB 重新读取校准状态。
         */
        private val calibratedSeqConversations: MutableSet<String> = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
                    size > MAX_CALIBRATED_CACHE_SIZE
            })
        )
        private val URL_REGEX = Regex("""https?://\S+|www\.\S+|\S+\.(com|cn|net|org|io)\b""", RegexOption.IGNORE_CASE)
        /** 普通用户撤回时间窗口：2 分钟 */
        private const val RECALL_TIME_LIMIT_MS = 2 * 60 * 1000L
        /** Seq key TTL: 每次快速路径发送顺延，避免长期冷会话 key 常驻 Redis */
        private val SEQ_KEY_TTL = java.time.Duration.ofDays(30)

        /**
         * Lua 脚本：原子 compare-and-set + INCR。
         * 如果当前 Redis 值 < MongoDB maxSeqId（ARGV[1]），先 SET 到 floor，再 INCR 返回。
         * 如果已 >= floor，直接 INCR。
         */
        private val SEQ_CALIBRATE_SCRIPT = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                local cur = tonumber(redis.call('GET', KEYS[1]) or '0')
                local floor = tonumber(ARGV[1])
                if cur < floor then
                    redis.call('SET', KEYS[1], tostring(floor))
                end
                return redis.call('INCR', KEYS[1])
                """.trimIndent()
            )
            resultType = Long::class.java
        }
    }

    private fun markCalibrated(conversationId: String) {
        calibratedSeqConversations.add(conversationId)
    }

    private fun incrementSeqWithTtl(seqKey: String): Long {
        @Suppress("UNCHECKED_CAST")
        val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
        val results = redisTemplate.executePipelined { connection ->
            val keyBytes = keySerializer.serialize(seqKey)!!
            connection.stringCommands().incr(keyBytes)
            connection.keyCommands().expire(keyBytes, SEQ_KEY_TTL.seconds)
            null
        }
        return when (val seq = results.getOrNull(0)) {
            is Number -> seq.toLong()
            is String -> seq.toLongOrNull() ?: 1L
            else -> 1L
        }
    }

    /**
     * 分配 seqId：持久化校准 + 分布式锁 + Lua 原子操作（P0-S6）。
     *
     * 流程：
     * 1. JVM 本地缓存命中 → 直接 Redis INCR（快速路径）
     * 2. 查 Conversation.seqCalibrated（走缓存）→ 如果 true，记入本地缓存后 INCR
     * 3. 否则获取分布式锁，双检查后执行 Lua 校准脚本，更新 Mongo 标记 + 失效缓存
     */
    private fun allocateSeqId(conversationId: String): Long {
        val seqKey = "rentmsg:seq:$conversationId"

        // 快速路径：JVM 本地缓存已标记为已校准
        if (conversationId in calibratedSeqConversations) {
            return incrementSeqWithTtl(seqKey)
        }

        // 查 MongoDB（经由缓存）判断是否已持久化校准
        val conv = conversationCacheService.getConversation(conversationId)
        if (conv?.seqCalibrated == true) {
            markCalibrated(conversationId)
            return incrementSeqWithTtl(seqKey)
        }

        // 慢路径：需要校准，尝试非阻塞获取分布式锁
        val handle = distributedLockService.tryLock("seqcal:$conversationId")
            ?: run {
                // Spin-retry: wait for the calibrating instance to finish
                var retryHandle: LockHandle? = null
                for (i in 1..3) {
                    Thread.sleep(50)
                    // Re-check: calibration might have completed during our wait
                    if (conversationId in calibratedSeqConversations) {
                        return incrementSeqWithTtl(seqKey)
                    }
                    val freshConv = conversationCacheService.getConversation(conversationId)
                    if (freshConv?.seqCalibrated == true) {
                        markCalibrated(conversationId)
                        return incrementSeqWithTtl(seqKey)
                    }
                    retryHandle = distributedLockService.tryLock("seqcal:$conversationId")
                    if (retryHandle != null) break
                }
                if (retryHandle == null) {
                    // Fallback: calibration still in progress, INCR anyway (may skip seqIds but won't lose messages)
                    log.warn("SeqId calibration lock contention for conv={}, falling back to direct INCR", conversationId)
                    return incrementSeqWithTtl(seqKey)
                }
                retryHandle
            }
        return try {
            // 双检查：锁内再读一次，可能其他实例已完成校准
            val freshConv = conversationRepository.findById(conversationId).orElse(null)
            if (freshConv?.seqCalibrated == true) {
                markCalibrated(conversationId)
                return incrementSeqWithTtl(seqKey)
            }

            // 从 MongoDB 读取当前最大 seqId
            val maxSeq = messageRepository.findByConversationIdOrderBySeqIdDesc(
                conversationId, PageRequest.of(0, 1)
            ).firstOrNull()?.seqId ?: 0L

            // Lua 原子操作：compare-and-set + INCR
            val newSeq = redisTemplate.execute(
                SEQ_CALIBRATE_SCRIPT,
                listOf(seqKey),
                maxSeq.toString()
            ) ?: 1L

            // 持久化校准标记到 MongoDB
            mongoTemplate.updateFirst(
                Query(Criteria.where("_id").`is`(conversationId)),
                Update().set("seqCalibrated", true),
                Conversation::class.java
            )

            // 失效缓存，让后续读取拉到最新（含 seqCalibrated=true）
            conversationCacheService.invalidate(conversationId)

            markCalibrated(conversationId)

            log.info("SeqId calibrated for conversation {}: mongoMax={}, newSeq={}", conversationId, maxSeq, newSeq)
            newSeq
        } finally {
            distributedLockService.unlock(handle)
        }
    }

    fun recallMessage(userId: String, messageId: String) {
        val msg = messageRepository.findById(messageId).orElseThrow { IllegalArgumentException("消息不存在") }
        val conversation = conversationCacheService.getConversation(msg.conversationId) ?: return

        // 群主/管理员可以撤回群内任意成员的消息，普通用户只能撤回自己的
        val isGroupAdmin = conversation.type == ConversationType.GROUP.value &&
            (userId == conversation.ownerId || userId in conversation.adminIds)

        if (msg.senderId != userId && !isGroupAdmin) {
            throw IllegalArgumentException("无权撤回此消息")
        }
        if (msg.contentType == ContentType.RED_PACKET.value) {
            throw IllegalArgumentException("红包消息无法撤回")
        }
        if (msg.contentType == ContentType.CALL.value) {
            throw IllegalArgumentException("通话记录无法撤回")
        }

        // 普通用户受 2 分钟撤回时间限制，群主/管理员不受此限制
        if (!isGroupAdmin) {
            val elapsed = System.currentTimeMillis() - msg.createdAt
            if (elapsed > RECALL_TIME_LIMIT_MS) {
                throw IllegalArgumentException("消息发送已超过2分钟，无法撤回")
            }
        }

        rabbitPublishService.send(
            RabbitMQConfig.MESSAGE_RECALL_QUEUE,
            mapOf("messageId" to messageId),
            "message_recall messageId=$messageId",
        )

        // 即时推送撤回通知
        val payload = objectMapper.writeValueAsString(mapOf(
            "type" to "message_recalled",
            "data" to mapOf(
                "messageId" to messageId,
                "conversationId" to msg.conversationId,
                "seqId" to msg.seqId
            )
        ))
        conversation.members.forEach { memberId ->
            if (conversation.type == ConversationType.GROUP.value) {
                messageBatchService.pushBatched(memberId, payload)
            } else {
                userSessionManager.pushToUser(memberId, payload, messageType = "message_recalled")
            }
        }
    }

    fun deleteMessage(userId: String, messageId: String, forAll: Boolean = false, sourceChannel: io.netty.channel.Channel? = null) {
        val msg = messageRepository.findById(messageId).orElseThrow { IllegalArgumentException("消息不存在") }
        val conversation = conversationCacheService.getConversation(msg.conversationId) ?: return

        if (conversation.type == ConversationType.SPECIAL_PRIVATE.value) {
            throw IllegalStateException("无法删除官方会话中的消息")
        }

        if (forAll) {
            val user = userCacheService.getUser(userId)
            if (msg.senderId != userId && user?.isOperator != true) {
                throw IllegalArgumentException("无权为所有人删除")
            }
            rabbitPublishService.send(
                RabbitMQConfig.MESSAGE_DELETE_QUEUE,
                mapOf("messageId" to messageId, "forAll" to true, "userId" to userId),
                "message_delete_all messageId=$messageId userId=$userId",
            )
            val payload = objectMapper.writeValueAsString(mapOf(
                "type" to "message_deleted",
                "data" to mapOf(
                    "messageId" to messageId,
                    "conversationId" to msg.conversationId,
                    "seqId" to msg.seqId
                )
            ))
            conversation.members.forEach { memberId ->
                if (conversation.type == ConversationType.GROUP.value) {
                    messageBatchService.pushBatched(memberId, payload)
                } else {
                    userSessionManager.pushToUser(memberId, payload, messageType = "message_deleted")
                }
            }
        } else {
            rabbitPublishService.send(
                RabbitMQConfig.MESSAGE_DELETE_QUEUE,
                mapOf("messageId" to messageId, "forAll" to false, "userId" to userId),
                "message_delete_local messageId=$messageId userId=$userId",
            )
            val syncPayload = objectMapper.writeValueAsString(mapOf(
                "type" to "message_deleted_local",
                "data" to mapOf(
                    "messageId" to messageId,
                    "conversationId" to msg.conversationId,
                    "seqId" to msg.seqId
                )
            ))
            if (sourceChannel != null) {
                userSessionManager.pushToUserExcluding(userId, syncPayload, sourceChannel)
            } else {
                userSessionManager.pushToUser(userId, syncPayload)
            }
        }
    }

    fun forwardMessage(userId: String, messageId: String, toConversationId: String): Message {
        val msg = messageRepository.findById(messageId).orElseThrow { IllegalArgumentException("消息不存在") }
        if (msg.isRecalled) {
            throw IllegalArgumentException("该消息已被撤回，无法转发")
        }
        if (msg.deletedBy.contains(userId)) {
            throw IllegalArgumentException("该消息已被删除，无法转发")
        }
        return sendMessage(userId, toConversationId, msg.content, msg.contentType)
    }

    fun forwardMessageBatch(userId: String, messageIds: List<String>, toConversationIds: List<String>) {
        val messages = messageRepository.findAllById(messageIds).sortedBy { it.seqId }
        val validMessages = messages.filter { !it.isRecalled && !it.deletedBy.contains(userId) }
        if (validMessages.isEmpty()) {
            throw IllegalArgumentException("没有可转发的消息")
        }
        for (convId in toConversationIds) {
            for (msg in validMessages) {
                sendMessage(userId, convId, msg.content, msg.contentType)
            }
        }
    }

    /**
     * 批量转换 DTO — 消除 N+1 查询
     */
    fun toDtos(messages: List<Message>): List<MessageDto> {
        if (messages.isEmpty()) return emptyList()

        // 1. 收集所有需要的 ID
        val replyMsgIds = messages.mapNotNull { it.replyToMessageId }.distinct()
        val convIds = messages.map { it.conversationId }.distinct()

        // 2. 批量查询（走快取）
        val replyMsgs = if (replyMsgIds.isNotEmpty())
            messageRepository.findAllById(replyMsgIds).associateBy { it.id }
        else emptyMap()

        val allUserIds = (messages.map { it.senderId } +
            replyMsgs.values.map { it.senderId }).distinct()
        val usersMap = userCacheService.getUsers(allUserIds)
        val convsMap = conversationCacheService.getConversations(convIds)

        // 群聊场景下批量取 myNickname：(conversationId, userId) -> nickname
        val groupConvIds = convsMap.values.filter { it.type == ConversationType.GROUP.value }.map { it.id!! }
        val nicknameMap: Map<Pair<String, String>, String?> = if (groupConvIds.isNotEmpty() && allUserIds.isNotEmpty()) {
            userConversationRepository.findByConversationIdInAndUserIdIn(groupConvIds, allUserIds)
                .associate { (it.conversationId to it.userId) to it.myNickname }
        } else emptyMap()

        // 3. 组装 DTO
        return messages.map { msg ->
            val conv = convsMap[msg.conversationId]
            val isGroup = conv?.type == ConversationType.GROUP.value
            val role = when {
                conv == null || !isGroup -> 0
                msg.senderId == conv.ownerId -> 2
                msg.senderId in conv.adminIds -> 1
                else -> 0
            }
            val replyToDto = msg.replyToMessageId?.let { replyId ->
                val origMsg = replyMsgs[replyId]
                origMsg?.let {
                    if (it.isRecalled) {
                        ReplyToDto(
                            messageId = it.id!!,
                            senderId = it.senderId,
                            content = "[消息已撤回]",
                            contentType = 0,
                            isRecalled = true
                        )
                    } else {
                        val origSender = usersMap[it.senderId]
                        val origNickname = if (isGroup) nicknameMap[msg.conversationId to it.senderId] else null
                        ReplyToDto(
                            messageId = it.id!!,
                            senderId = it.senderId,
                            senderName = origNickname ?: origSender?.displayName,
                            content = normalizeAndRepairOutgoingContent(it.content, it.contentType),
                            contentType = it.contentType
                        )
                    }
                }
            }
            val sender = usersMap[msg.senderId]
            val senderNickname = if (isGroup) nicknameMap[msg.conversationId to msg.senderId] else null
            MessageDto(
                id = msg.id!!,
                conversationId = msg.conversationId,
                senderId = msg.senderId,
                senderName = senderNickname ?: sender?.displayName,
                senderAvatar = sender?.avatarUrl,
                contentType = msg.contentType,
                content = normalizeAndRepairOutgoingContent(msg.content, msg.contentType),
                seqId = msg.seqId,
                isRecalled = msg.isRecalled,
                mentions = msg.mentions,
                createdAt = msg.createdAt,
                replyTo = replyToDto,
                senderRole = role,
                senderIsBot = sender?.isBot ?: false,
                groupName = if (conv?.type == ConversationType.GROUP.value) conv.name else null,
                groupAvatar = if (conv?.type == ConversationType.GROUP.value) conv.avatarUrl else null
            )
        }
    }

    private fun contentTypeName(type: Int): String = when (type) {
        ContentType.TEXT.value -> "文本"
        ContentType.IMAGE.value -> "图片"
        ContentType.VOICE.value -> "语音"
        ContentType.VIDEO.value -> "视频"
        ContentType.STICKER.value -> "表情"
        ContentType.SYSTEM.value -> "系统通知"
        ContentType.BUSINESS_CARD.value -> "名片"
        else -> "消息"
    }
}
