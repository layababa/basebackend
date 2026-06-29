package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.dto.FriendDto
import com.layababateam.xinxiwang_backend.dto.FriendRequestDto
import com.layababateam.xinxiwang_backend.dto.FriendSyncResponse
import com.layababateam.xinxiwang_backend.model.*
import com.layababateam.xinxiwang_backend.repository.*
import com.layababateam.xinxiwang_backend.service.cache.FriendshipCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class FriendService(
    private val friendshipRepository: FriendshipRepository,
    private val friendRequestRepository: FriendRequestRepository,
    private val conversationRepository: ConversationRepository,
    private val userConversationRepository: UserConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val userSessionManager: UserSessionManager,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val userCacheService: UserCacheService,
    private val friendshipCacheService: FriendshipCacheService,
    @org.springframework.context.annotation.Lazy private val messageService: MessageService,
    private val userConversationCacheService: UserConversationCacheService
) {
    private val log = LoggerFactory.getLogger(FriendService::class.java)

    companion object {
        private const val FRIEND_VERSION_KEY = "rentmsg:friend:version:"
        private const val MAX_SYNC_LIMIT = 50
        private val calibratedVersionUsers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }

    /**
     * 為指定用戶產生下一個好友版本號。
     * 每個用戶在服務啟動後首次操作時從 MongoDB 校準，之後直接用 Redis。
     */
    private fun nextVersion(userId: String): Long {
        val key = "$FRIEND_VERSION_KEY$userId"
        val redisVal = redisTemplate.opsForValue().increment(key) ?: 1L
        if (userId in calibratedVersionUsers) return redisVal
        val maxVersion = friendshipRepository.findTopByUserIdOrderByVersionDesc(userId)?.version ?: 0L
        calibratedVersionUsers.add(userId)
        if (redisVal <= maxVersion) {
            redisTemplate.opsForValue().set(key, (maxVersion + 1).toString())
            return maxVersion + 1
        }
        return redisVal
    }

    /**
     * 群内加好友权限校验：仅当 client 明确提供 fromGroupId（表示加好友动作发起
     * 自某个具体的群上下文，例如群成员列表 / 群内消息气泡头像）时调用。
     *
     * 群外入口（通讯录搜索、扫码、手机号查找、名片推荐等）一律不传 fromGroupId，
     * 不走此校验，因此不受任何群 addFriendMode 约束。
     *
     * 服务端对 fromGroupId 做完整性校验，防止 client 伪造 groupId 冒充身份：
     *   - viewer == target / Bot / 官方账号 / 已是好友 → 放行
     *   - groupId 不存在 / 非群聊 / viewer 或 target 不是成员 → 视同群外入口放行
     *   - 按群 addFriendMode 判定（归一后 0=所有人 / 1=仅群主 / 2=仅群主或管理员）
     */
    fun checkInGroupAddFriendPermission(viewerId: String, target: User, groupId: String) {
        val targetId = target.id ?: return
        if (viewerId == targetId) return
        if (target.isBot || target.isOperator) return
        if (friendshipRepository.findByUserIdAndFriendId(viewerId, targetId) != null) return

        val group = conversationRepository.findById(groupId).orElse(null) ?: return
        if (group.type != 1) return
        if (viewerId !in group.members || targetId !in group.members) return

        val mode = GroupSettingsService.normalizeAddFriendMode(group.addFriendMode)
        val allowed = when (mode) {
            0 -> true
            1 -> group.ownerId == viewerId
            2 -> group.ownerId == viewerId || viewerId in group.adminIds
            else -> true
        }
        if (!allowed) {
            log.info(
                "[GroupAddFriendBlocked] viewer={} target={} group={} mode={}",
                viewerId, targetId, groupId, mode
            )
            throw IllegalStateException(
                when (mode) {
                    1 -> "仅群主可添加好友"
                    2 -> "仅群主/管理员可添加好友"
                    else -> "此群禁止添加好友"
                }
            )
        }
    }

    /**
     * 校验名片来源的合法性。要绕过群权限检查，必须全部满足：
     *   1. 指定的 sourceCardMessageId 消息存在
     *   2. contentType == 13（仅个人名片；群名片 14 走加群流程，不适用）
     *   3. 请求者 ≠ 消息发送者（必须是别人推荐给你的，不能自建"假名片"绕过）
     *   4. 名片 payload 的 userId 与当前 toUserId 一致（防止拿 A 的名片加 B）
     *   5. 请求者是该消息所在会话的成员（证明确实收到过这张名片）
     *
     * 群权限（addFriendMode）不限制"通过名片添加好友"—— 无论名片是在私聊还是
     * 群聊里被推荐，只要通过上述校验就允许加好友。名片是发送方的定向担保行为。
     */
    private fun isValidCardSource(requesterId: String, toUserId: String, sourceCardMessageId: String?): Boolean {
        if (sourceCardMessageId.isNullOrBlank()) return false
        val msg = messageRepository.findById(sourceCardMessageId).orElse(null) ?: return false
        if (msg.contentType != 13) return false
        if (msg.senderId == requesterId) return false

        val cardUserId = try {
            val node = objectMapper.readTree(msg.content)
            node?.get("userId")?.asText()
        } catch (_: Exception) {
            null
        }
        if (cardUserId.isNullOrBlank() || cardUserId != toUserId) return false

        val conv = conversationRepository.findById(msg.conversationId).orElse(null) ?: return false
        if (requesterId !in conv.members) return false

        return true
    }

    /**
     * 发送好友请求
     */
    fun sendRequest(
        fromUserId: String,
        toUserIdOrUsername: String,
        message: String,
        fromGroupId: String? = null,
        sourceCardMessageId: String? = null
    ): FriendRequest {
        // Resolve toUser — accept either an ObjectId or a username so the API
        // is robust regardless of what the client passes in.
        val toUser = userRepository.findById(toUserIdOrUsername).orElse(null)
            ?: userRepository.findByUsername(toUserIdOrUsername)
            ?: throw IllegalArgumentException("用户不存在: $toUserIdOrUsername")
        val toUserId = toUser.id!!

        // 加好友权限判定：
        //   - 名片来源合法 → 绕过群权限（名片定向担保）
        //   - 否则若 client 明确传 fromGroupId → 按该群 addFriendMode 判定
        //   - 否则（通讯录搜索、扫码、手机号查找等群外入口） → 直接放行
        when {
            isValidCardSource(fromUserId, toUserId, sourceCardMessageId) -> {
                log.info(
                    "[FriendAdd] bypass via card source: requester={} target={} msgId={}",
                    fromUserId, toUserId, sourceCardMessageId
                )
            }
            fromGroupId != null -> checkInGroupAddFriendPermission(fromUserId, toUser, fromGroupId)
        }

        // 检查是否已是好友
        if (friendshipRepository.findByUserIdAndFriendId(fromUserId, toUserId) != null) {
            throw IllegalStateException("已经是好友了")
        }

        // 检查是否被对方永久拒绝
        val permanentlyRejected = friendRequestRepository.findByFromUserIdAndToUserIdAndStatus(fromUserId, toUserId, 3)
        if (permanentlyRejected != null) {
            throw IllegalStateException("对方已拒绝接收你的好友请求")
        }

        // 检查是否已有待处理的请求
        val existing = friendRequestRepository.findByFromUserIdAndToUserIdAndStatus(fromUserId, toUserId, 0)
        if (existing != null) {
            throw IllegalStateException("好友请求已发送，等待对方确认")
        }

        // 检查反向是否有待处理的请求（对方也在加你）
        val reverseRequest = friendRequestRepository.findByFromUserIdAndToUserIdAndStatus(toUserId, fromUserId, 0)
        if (reverseRequest != null) {
            // 自动接受反向请求，直接建立好友关系
            acceptRequest(reverseRequest.id!!, fromUserId)
            // 将当前请求保存为已接受状态
            val autoAccepted = FriendRequest(
                fromUserId = fromUserId,
                toUserId = toUserId,
                message = message,
                status = 1,
                updatedAt = System.currentTimeMillis()
            )
            val saved = friendRequestRepository.save(autoAccepted)
            log.info("Reverse pending request detected. Auto-accepted between {} and {}", fromUserId, toUserId)
            return saved
        }

        // Bot 自动接受好友请求
        if (toUser.isBot) {
            val autoRequest = friendRequestRepository.save(
                FriendRequest(fromUserId = fromUserId, toUserId = toUserId, message = message, status = 0)
            )
            acceptRequest(autoRequest.id!!, toUserId)
            log.info("Bot auto-accepted friend request from {} to bot {}", fromUserId, toUserId)
            return autoRequest
        }

        val request = FriendRequest(
            fromUserId = fromUserId,
            toUserId = toUserId,
            message = message
        )
        val saved = friendRequestRepository.save(request)

        // 查发起者信息用于推送通知
        val fromUser = userCacheService.getUser(fromUserId)

        rabbitPublishService.send(
            RabbitMQConfig.EVENT_FRIEND_EXCHANGE,
            "",
            mapOf(
                "type" to "friend_request_notification",
                "targetUserIds" to listOf(toUserId),
                "data" to mapOf(
                    "requestId" to saved.id,
                    "fromUserId" to fromUserId,
                    "fromUserDisplayName" to (fromUser?.displayName ?: ""),
                    "fromUserUsername" to (fromUser?.username ?: ""),
                    "fromUserAvatarUrl" to (fromUser?.avatarUrl ?: ""),
                    "message" to message,
                    "createdAt" to saved.createdAt
                )
            ),
            "friend_request_notification from=$fromUserId to=$toUserId",
        )

        log.info("Friend request sent from {} to {}", fromUserId, toUserId)
        return saved
    }

    /**
     * 接受好友请求：双向插入 Friendship + 创建 Conversation + UserConversation
     */
    fun acceptRequest(requestId: String, userId: String): Friendship {
        val request = friendRequestRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("请求不存在") }

        if (request.toUserId != userId) {
            throw IllegalArgumentException("无权接受此请求")
        }
        if (request.status != 0) {
            throw IllegalStateException("请求已处理")
        }

        val fromUserId = request.fromUserId
        val toUserId = request.toUserId

        // 幂等性检查：如果已经是好友，直接返回已有的 Friendship
        val existingFriendship = friendshipRepository.findByUserIdAndFriendId(fromUserId, toUserId)
        if (existingFriendship != null) {
            friendRequestRepository.save(request.copy(status = 1, updatedAt = System.currentTimeMillis()))
            log.info("Friendship already exists between {} and {}, skipping duplicate creation", fromUserId, toUserId)
            return existingFriendship
        }

        // 更新请求状态
        friendRequestRepository.save(request.copy(status = 1, updatedAt = System.currentTimeMillis()))

        val now = System.currentTimeMillis()

        // 复用已有私聊会话，避免重复创建
        val conversation = conversationRepository.findPrivateChatByMembers(listOf(fromUserId, toUserId), 0)
            ?: conversationRepository.save(
                Conversation(
                    type = 0,
                    members = listOf(fromUserId, toUserId),
                    createdAt = now
                )
            )

        // 双向插入好友关系（带版本号）
        val v1 = nextVersion(fromUserId)
        val v2 = nextVersion(toUserId)
        val friendship1 = friendshipRepository.save(
            Friendship(userId = fromUserId, friendId = toUserId, conversationId = conversation.id!!, createdAt = now, version = v1, updatedAt = now)
        )
        friendshipRepository.save(
            Friendship(userId = toUserId, friendId = fromUserId, conversationId = conversation.id!!, createdAt = now, version = v2, updatedAt = now)
        )

        // 为双方创建 UserConversation（如果不存在）；若已存在且之前被"非好友删除"打了水位线/软删，重置为可见
        val fromUc = userConversationRepository.findFirstByUserIdAndConversationId(fromUserId, conversation.id!!)
        if (fromUc == null) {
            userConversationRepository.save(
                UserConversation(userId = fromUserId, conversationId = conversation.id!!, lastReadTime = now, createdAt = now)
            )
        } else if (fromUc.hiddenBeforeSeqId != 0L || fromUc.deleted) {
            userConversationRepository.save(fromUc.copy(hiddenBeforeSeqId = 0L, deleted = false))
            log.info("Reset hidden watermark on re-friend: user={} conv={}", fromUserId, conversation.id)
        }
        val toUc = userConversationRepository.findFirstByUserIdAndConversationId(toUserId, conversation.id!!)
        if (toUc == null) {
            userConversationRepository.save(
                UserConversation(userId = toUserId, conversationId = conversation.id!!, lastReadTime = now, createdAt = now)
            )
        } else if (toUc.hiddenBeforeSeqId != 0L || toUc.deleted) {
            userConversationRepository.save(toUc.copy(hiddenBeforeSeqId = 0L, deleted = false))
            log.info("Reset hidden watermark on re-friend: user={} conv={}", toUserId, conversation.id)
        }

        userConversationCacheService.invalidate(fromUserId)
        userConversationCacheService.invalidate(toUserId)

        // Invalidate friendship cache for both users
        friendshipCacheService.invalidate(fromUserId)
        friendshipCacheService.invalidate(toUserId)

        val acceptor = userCacheService.getUser(toUserId)
        rabbitPublishService.send(
            RabbitMQConfig.EVENT_FRIEND_EXCHANGE,
            "",
            mapOf(
                "type" to "friend_accepted_notification",
                "targetUserIds" to listOf(fromUserId),
                "data" to mapOf(
                    "requestId" to requestId,
                    "userId" to toUserId,
                    "userName" to (acceptor?.displayName ?: ""),
                    "userAvatar" to (acceptor?.avatarUrl ?: ""),
                    "conversationId" to conversation.id
                )
            ),
            "friend_accepted_notification request=$requestId target=$fromUserId",
        )

        // 由后端发送欢迎消息，避免前端阻塞 UI
        try {
            messageService.sendMessage(
                senderId = toUserId,
                conversationId = conversation.id!!,
                content = "我已经通过了你的好友请求，现在开始聊天吧",
                contentType = 0
            )
        } catch (e: Exception) {
            log.warn("Failed to send welcome message for request {}: {}", requestId, e.message)
        }

        log.info("Friend request {} accepted. Friendship created between {} and {}", requestId, fromUserId, toUserId)
        return friendship1
    }

    /**
     * 拒绝好友请求
     */
    fun rejectRequest(requestId: String, userId: String, permanent: Boolean = false) {
        val request = friendRequestRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("请求不存在") }
        if (request.toUserId != userId) {
            throw IllegalArgumentException("无权拒绝此请求")
        }
        if (request.status != 0) {
            throw IllegalStateException("请求已处理")
        }
        val newStatus = if (permanent) 3 else 2
        friendRequestRepository.save(request.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
        log.info("Friend request {} {} by {}", requestId, if (permanent) "permanently rejected" else "rejected", userId)
    }

    /**
     * 获取好友列表（带用户信息）
     */
    fun getFriendList(userId: String): List<FriendDto> {
        val friendships = friendshipRepository.findByUserId(userId)
        if (friendships.isEmpty()) return emptyList()

        val activeFriendships = friendships.filter { it.blockedAt == null }

        val friendIds = activeFriendships.map { it.friendId }
        val users = userCacheService.getUsers(friendIds)

        return activeFriendships.mapNotNull { fs ->
            val user = users[fs.friendId] ?: return@mapNotNull null
            FriendDto(
                userId = user.id!!,
                username = user.username,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                gender = user.gender,
                bio = user.bio,
                conversationId = fs.conversationId,
                remark = fs.remark,
                version = fs.version
            )
        }
    }

    /**
     * 获取待处理的好友请求列表
     */
    fun getPendingRequests(userId: String): List<FriendRequestDto> {
        val requests = friendRequestRepository.findByToUserIdAndStatus(userId, 0)
        val fromUserIds = requests.map { it.fromUserId }
        val users = userCacheService.getUsers(fromUserIds)

        return requests.map { req ->
            val fromUser = users[req.fromUserId]
            FriendRequestDto(
                id = req.id!!,
                fromUserId = req.fromUserId,
                fromUserDisplayName = fromUser?.displayName,
                fromUserUsername = fromUser?.username,
                fromUserAvatarUrl = fromUser?.avatarUrl,
                toUserId = req.toUserId,
                message = req.message,
                status = req.status,
                createdAt = req.createdAt,
                updatedAt = req.updatedAt
            )
        }
    }

    /**
     * 获取所有好友请求记录（含 pending / accepted / rejected），
     * 包括发出的和收到的请求。
     */
    fun getAllRequests(userId: String): List<FriendRequestDto> {
        val requests = friendRequestRepository.findByFromUserIdOrToUserId(userId, userId)
            .sortedByDescending { it.createdAt }
        val relatedUserIds = requests.flatMap { listOf(it.fromUserId, it.toUserId) }
            .filter { it != userId }
            .distinct()
        val users = userCacheService.getUsers(relatedUserIds)

        return requests.map { req ->
            val isOutgoing = req.fromUserId == userId
            val otherUserId = if (isOutgoing) req.toUserId else req.fromUserId
            val otherUser = users[otherUserId]
            FriendRequestDto(
                id = req.id!!,
                fromUserId = req.fromUserId,
                fromUserDisplayName = otherUser?.displayName,
                fromUserUsername = otherUser?.username,
                fromUserAvatarUrl = otherUser?.avatarUrl,
                toUserId = req.toUserId,
                message = req.message,
                status = req.status,
                createdAt = req.createdAt,
                updatedAt = req.updatedAt,
                isOutgoing = isOutgoing
            )
        }
    }

    /**
     * 删除好友（双向删除关系，会话保留）
     */
    fun deleteFriend(userId: String, friendId: String) {
        friendshipRepository.deleteByUserIdAndFriendId(userId, friendId)
        friendshipRepository.deleteByUserIdAndFriendId(friendId, userId)
        friendshipCacheService.invalidate(userId)
        friendshipCacheService.invalidate(friendId)

        // 清理双方之间所有 pending 的好友请求，避免重新添加时产生冲突
        val now = System.currentTimeMillis()
        val pendingRequest1 = friendRequestRepository.findByFromUserIdAndToUserIdAndStatus(userId, friendId, 0)
        val pendingRequest2 = friendRequestRepository.findByFromUserIdAndToUserIdAndStatus(friendId, userId, 0)
        listOfNotNull(pendingRequest1, pendingRequest2).forEach { req ->
            friendRequestRepository.save(req.copy(status = 2, updatedAt = now))
        }

        // 查找会话 ID（用于客户端禁用会话）
        val conversation = conversationRepository.findPrivateChatByMembers(listOf(userId, friendId), 0)
        val conversationId = conversation?.id ?: ""

        log.info("Friendship deleted between {} and {}", userId, friendId)

        // 通知被删方（所有设备）
        val notifyDeleted = objectMapper.writeValueAsString(
            mapOf("type" to "friend_deleted_notification", "data" to mapOf(
                "friendId" to userId,
                "conversationId" to conversationId
            ))
        )
        userSessionManager.pushToUser(friendId, notifyDeleted)

        // 通知操作方的其他设备（多端同步）
        val notifySelf = objectMapper.writeValueAsString(
            mapOf("type" to "friend_deleted_notification", "data" to mapOf(
                "friendId" to friendId,
                "conversationId" to conversationId
            ))
        )
        userSessionManager.pushToUser(userId, notifySelf)
    }

    /**
     * 拉黑好友（单向：userId 拉黑 friendId）
     */
    fun blockFriend(userId: String, friendId: String) {
        val friendship = friendshipRepository.findByUserIdAndFriendId(userId, friendId)
            ?: throw IllegalArgumentException("好友关系不存在")
        val now = System.currentTimeMillis()
        friendshipRepository.save(friendship.copy(
            blockedAt = now, version = nextVersion(userId), updatedAt = now
        ))
        friendshipCacheService.invalidate(userId)
        log.info("User {} blocked friend {}", userId, friendId)

        // 通知操作方的其他设备（多端同步拉黑状态）
        val notification = objectMapper.writeValueAsString(
            mapOf("type" to "friend_blocked_notification", "data" to mapOf(
                "friendId" to friendId
            ))
        )
        userSessionManager.pushToUser(userId, notification)
    }

    /**
     * 解除拉黑
     */
    fun unblockFriend(userId: String, friendId: String) {
        val friendship = friendshipRepository.findByUserIdAndFriendId(userId, friendId)
            ?: throw IllegalArgumentException("好友关系不存在")
        val now = System.currentTimeMillis()
        friendshipRepository.save(friendship.copy(
            blockedAt = null, version = nextVersion(userId), updatedAt = now
        ))
        friendshipCacheService.invalidate(userId)
        log.info("User {} unblocked friend {}", userId, friendId)
    }

    /**
     * 增量好友同步：返回 version > afterVersion 的好友列表。
     * 同時返回當前全部好友 ID 列表，客戶端可據此檢測已刪除的好友。
     */
    fun getFriendsAfterVersion(userId: String, afterVersion: Long, limit: Int): FriendSyncResponse {
        val safeLimit = limit.coerceIn(1, MAX_SYNC_LIMIT)
        val changed = friendshipRepository.findByUserIdAndVersionGreaterThanOrderByVersionAsc(
            userId, afterVersion, PageRequest.of(0, safeLimit)
        )

        val friendIds = changed.map { it.friendId }
        val users = if (friendIds.isNotEmpty()) {
            userCacheService.getUsers(friendIds)
        } else {
            emptyMap()
        }

        val friends = changed.mapNotNull { fs ->
            val user = users[fs.friendId] ?: return@mapNotNull null
            FriendDto(
                userId = user.id!!,
                username = user.username,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                gender = user.gender,
                bio = user.bio,
                conversationId = fs.conversationId,
                remark = fs.remark,
                version = fs.version
            )
        }

        val latestVersion = changed.maxOfOrNull { it.version }
            ?: friendshipRepository.findTopByUserIdOrderByVersionDesc(userId)?.version
            ?: 0L

        // 取全部好友 ID 讓客戶端做差集判斷刪除
        val allFriendIds = friendshipRepository.findByUserId(userId).map { it.friendId }

        return FriendSyncResponse(
            friends = friends,
            allFriendIds = allFriendIds,
            latestVersion = latestVersion,
            hasMore = changed.size >= safeLimit
        )
    }
}
