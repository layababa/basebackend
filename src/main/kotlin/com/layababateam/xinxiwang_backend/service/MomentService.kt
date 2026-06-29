package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.*
import com.layababateam.xinxiwang_backend.model.*
import com.layababateam.xinxiwang_backend.repository.*
import com.layababateam.xinxiwang_backend.service.cache.FriendshipCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
class MomentService(
    private val momentRepository: MomentRepository,
    private val momentMediaRepository: MomentMediaRepository,
    private val momentCommentRepository: MomentCommentRepository,
    private val momentLikeRepository: MomentLikeRepository,
    private val userRelationSettingRepository: UserRelationSettingRepository,
    private val userRepository: UserRepository,
    private val friendshipRepository: FriendshipRepository,
    private val redisTemplate: StringRedisTemplate,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val userCacheService: UserCacheService,
    private val friendshipCacheService: FriendshipCacheService
) {
    private val log = LoggerFactory.getLogger(MomentService::class.java)
    // === 1. 发佈与删除动态 ===

    fun publishMoment(userId: String, request: PublishMomentRequest): ApiResponse<Map<String, String>> {
        val user = userCacheService.getUser(userId)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "用户不存在")

        // 1. 保存动态主表
        val moment = Moment(
            userId = userId,
            content = request.content,
            location = request.location,
            visibilityType = request.visibilityType,
            visibilityList = request.visibilityList
        )
        val savedMoment = momentRepository.save(moment)
        val momentId = savedMoment.id!!

        // 2. 保存媒体附件
        val medias = request.medias.map {
            MomentMedia(
                momentId = momentId,
                url = it.url,
                type = it.type,
                sortOrder = it.sortOrder,
                thumbnailUrl = it.thumbnailUrl
            )
        }
        if (medias.isNotEmpty()) {
            momentMediaRepository.saveAll(medias)
        }

        return ApiResponse.ok(mapOf("momentId" to momentId), "发布成功")
    }

    fun deleteMoment(userId: String, momentId: String): ApiResponse<Any> {
        val moment = momentRepository.findById(momentId).orElse(null)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "动态不存在")

        if (moment.userId != userId) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "无权删除该动态")
        }

        momentMediaRepository.deleteByMomentId(momentId)
        momentLikeRepository.deleteByMomentId(momentId)
        momentCommentRepository.deleteByMomentId(momentId)
        momentRepository.deleteById(momentId)

        val friendIds = friendshipCacheService.getFriendIds(userId)
        if (friendIds.isNotEmpty()) {
            rabbitPublishService.send(
                RabbitMQConfig.EVENT_FRIEND_EXCHANGE,
                "",
                mapOf(
                    "type" to "moment_deleted",
                    "targetUserIds" to friendIds.toList(),
                    "data" to mapOf("momentId" to momentId, "userId" to userId),
                ),
                "moment_deleted user=$userId momentId=$momentId",
            )
        }

        return ApiResponse.ok(message = "删除成功")
    }

    // === 2. 互动 (点赞/评论) ===

    companion object {
        private const val MOMENTS_UNREAD_KEY_PREFIX = "rentmsg:moments:unread:"
    }

    fun likeMoment(userId: String, momentId: String): ApiResponse<Any> {
        val moment = momentRepository.findById(momentId).orElse(null)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "动态不存在或已被删除")

        val existingLike = momentLikeRepository.findByMomentIdAndUserId(momentId, userId)
        if (existingLike != null) {
            return ApiResponse.ok(message = "已点赞")
        }

        val like = MomentLike(momentId = momentId, userId = userId)
        momentLikeRepository.save(like)

        // 给动态发布者增加未读互动计数（不给自己点赞计数）
        if (moment.userId != userId) {
            incrementUnreadCount(moment.userId)
        }

        return ApiResponse.ok(message = "点赞成功")
    }

    fun unlikeMoment(userId: String, momentId: String): ApiResponse<Any> {
        val moment = momentRepository.findById(momentId).orElse(null)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "动态不存在或已被删除")

        val existingLike = momentLikeRepository.findByMomentIdAndUserId(momentId, userId)
        if (existingLike != null) {
            momentLikeRepository.delete(existingLike)
            if (moment.userId != userId) {
                decrementUnreadCount(moment.userId)
            }
        }
        return ApiResponse.ok(message = "取消点赞成功")
    }

    fun addComment(userId: String, request: AddCommentRequest): ApiResponse<Any> {
        val moment = momentRepository.findById(request.momentId).orElse(null)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "动态不存在或已被删除")

        val comment = MomentComment(
            momentId = request.momentId,
            userId = userId,
            replyToUserId = request.replyToUserId,
            content = request.content
        )
        val saved = momentCommentRepository.save(comment)

        // 给动态发布者增加未读互动计数（不给自己评论计数）
        if (moment.userId != userId) {
            incrementUnreadCount(moment.userId)
        }

        return ApiResponse.ok(
            mapOf("commentId" to saved.id, "createdAt" to saved.createdAt),
            "评论成功"
        )
    }

    fun deleteComment(userId: String, commentId: String): ApiResponse<Any> {
        val comment = momentCommentRepository.findById(commentId).orElse(null)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "评论不存在")

        val moment = momentRepository.findById(comment.momentId).orElse(null)
        if (comment.userId != userId && moment?.userId != userId) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "无权删除该评论")
        }

        momentCommentRepository.delete(comment)
        return ApiResponse.ok(message = "评论删除成功")
    }

    // === 3. 获取朋友圈 Feed 流 ===

    fun getTimeline(userId: String, page: Int = 0, size: Int = 10): TimelineResponse {
        val friendIds = friendshipCacheService.getFriendIds(userId)

        // 过滤掉"我不看他的朋友圈"的用户
        val hiddenByMe = userRelationSettingRepository.findByUserIdAndHideHisMomentsTrue(userId)
            .map { it.targetUserId }.toSet()
        // 过滤掉"不让我看他朋友圈"的用户（对方设置了不让我看）
        val hiddenFromMe = userRelationSettingRepository.findByTargetUserIdAndHideMyMomentsTrue(userId)
            .map { it.userId }.toSet()

        val visibleUserIds = ((friendIds - hiddenByMe - hiddenFromMe) + userId).toList()

        log.info("[Timeline] userId={}, friendCount={}, hiddenByMe={}, hiddenFromMe={}, visibleUserIds={}", userId, friendIds.size, hiddenByMe.size, hiddenFromMe.size, visibleUserIds)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val momentPage = momentRepository.findByUserIdIn(visibleUserIds, pageable)
        val moments = momentPage.content

        // 批量預取所有動態發佈者的用戶資訊，做 momentsVisibility 過濾
        val publisherIds = moments.map { it.userId }.distinct()
        val publishersMap = userCacheService.getUsers(publisherIds)

        val filtered = moments.filter { moment ->
            if (moment.userId == userId) return@filter true
            // 检查 visibilityType（PRIVATE/PARTIAL_VISIBLE/INVISIBLE_TO）
            if (!isMomentVisibleToUser(moment, userId)) return@filter false
            val publisher = publishersMap[moment.userId] ?: return@filter false
            isMomentVisibleByTime(publisher.momentsVisibility, moment.createdAt)
        }

        log.info("[Timeline] found {} moments (filtered from {}) for userId={}", filtered.size, moments.size, userId)

        // hasMore 基于数据库分页判断，而非过滤后数量，避免误判
        val hasMore = momentPage.hasNext()
        return TimelineResponse(moments = mapToMomentDtos(userId, filtered), hasMore = hasMore)
    }

    // === 4. 获取特定好友的朋友圈 (好友个人主页入口) ===

    fun getUserMoments(currentUserId: String, targetUserId: String, page: Int = 0, size: Int = 10): List<MomentDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        // 自己查看自己的朋友圈，不做時間過濾，直接資料庫分頁
        if (currentUserId == targetUserId) {
            val moments = momentRepository.findByUserId(targetUserId, pageable).content
            return mapToMomentDtos(currentUserId, moments)
        }

        // 检查是否屏蔽 (不让他看 / 不看他)
        val targetSetting = userRelationSettingRepository.findByUserIdAndTargetUserId(targetUserId, currentUserId)
        if (targetSetting?.hideMyMoments == true) {
            return emptyList() // 对方设置了不让我看
        }
        val mySetting = userRelationSettingRepository.findByUserIdAndTargetUserId(currentUserId, targetUserId)
        if (mySetting?.hideHisMoments == true) {
            return emptyList() // 我设置了不看他的朋友圈
        }

        // 根據對方的 momentsVisibility 做時間過濾，下推至資料庫
        val targetUser = userCacheService.getUser(targetUserId)
            ?: return emptyList()

        val visibilityCutoff = getVisibilityCutoff(targetUser.momentsVisibility)
        val moments = if (visibilityCutoff != null) {
            if (visibilityCutoff == Long.MAX_VALUE) {
                // "none" — 不可見
                emptyList()
            } else {
                momentRepository.findByUserIdAndCreatedAtGreaterThan(targetUserId, visibilityCutoff, pageable).content
            }
        } else {
            // "all" — 無時間限制
            momentRepository.findByUserId(targetUserId, pageable).content
        }

        // 按 visibilityType 过滤（PRIVATE/PARTIAL_VISIBLE/INVISIBLE_TO）
        val filtered = moments.filter { isMomentVisibleToUser(it, currentUserId) }

        return mapToMomentDtos(currentUserId, filtered)
    }

    // === 5. 隐私设定 API ===

    fun updateGlobalPrivacy(userId: String, request: UpdateGlobalPrivacyRequest): ApiResponse<Any> {
        val user = userRepository.findById(userId).orElse(null)
            ?: return ApiResponse.error(ErrorCode.NOT_FOUND, "用户不存在")

        userRepository.save(user.copy(momentsVisibility = request.momentsVisibility))
        userCacheService.invalidate(userId)
        return ApiResponse.ok(message = "全局隐私设置更新成功")
    }

    fun getRelationSetting(userId: String, targetUserId: String): RelationSettingDto {
        val setting = userRelationSettingRepository.findByUserIdAndTargetUserId(userId, targetUserId)
        return RelationSettingDto(
            targetUserId = targetUserId,
            hideMyMoments = setting?.hideMyMoments ?: false,
            hideHisMoments = setting?.hideHisMoments ?: false
        )
    }

    fun updateRelationSetting(userId: String, request: UpdateRelationSettingRequest): ApiResponse<Any> {
        var setting = userRelationSettingRepository.findByUserIdAndTargetUserId(userId, request.targetUserId)
        if (setting == null) {
            setting = UserRelationSetting(
                userId = userId,
                targetUserId = request.targetUserId,
                hideMyMoments = request.hideMyMoments,
                hideHisMoments = request.hideHisMoments
            )
        } else {
            setting = setting.copy(
                hideMyMoments = request.hideMyMoments,
                hideHisMoments = request.hideHisMoments
            )
        }
        userRelationSettingRepository.save(setting)
        return ApiResponse.ok(message = "关系设置更新成功")
    }

    // === 6. 朋友圈未读互动计数 ===

    fun getUnreadCount(userId: String): Int {
        val count = redisTemplate.opsForValue().get("${MOMENTS_UNREAD_KEY_PREFIX}$userId")
        return count?.toIntOrNull() ?: 0
    }

    fun clearUnreadCount(userId: String) {
        redisTemplate.delete("${MOMENTS_UNREAD_KEY_PREFIX}$userId")
    }

    private fun incrementUnreadCount(userId: String) {
        redisTemplate.opsForValue().increment("${MOMENTS_UNREAD_KEY_PREFIX}$userId")
    }

    private fun decrementUnreadCount(userId: String) {
        val key = "${MOMENTS_UNREAD_KEY_PREFIX}$userId"
        val current = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0
        if (current > 0) {
            redisTemplate.opsForValue().decrement(key)
        }
    }

    // === 7. 最新动态时间戳（轻量级红点检查） ===

    fun getLatestMomentTimestamp(userId: String): Long {
        val friendIds = friendshipCacheService.getFriendIds(userId)

        val hiddenByMe = userRelationSettingRepository.findByUserIdAndHideHisMomentsTrue(userId)
            .map { it.targetUserId }.toSet()
        val hiddenFromMe = userRelationSettingRepository.findByTargetUserIdAndHideMyMomentsTrue(userId)
            .map { it.userId }.toSet()

        val visibleUserIds = ((friendIds - hiddenByMe - hiddenFromMe) + userId).toList()
        if (visibleUserIds.isEmpty()) return 0L

        val latestMoment = momentRepository.findFirstByUserIdInOrderByCreatedAtDesc(visibleUserIds)
        return latestMoment?.createdAt ?: 0L
    }

    // === 私有辅助方法 ===

    /**
     * 根据动态的 visibilityType 判断该动态对指定用户是否可见。
     * 注意：动态发布者自己始终可见（应在调用前判断）。
     */
    private fun isMomentVisibleToUser(moment: Moment, viewerId: String): Boolean {
        // 发布者自己始终可见
        if (moment.userId == viewerId) return true

        return when (moment.visibilityType) {
            VisibilityType.PUBLIC -> true
            VisibilityType.PRIVATE -> false
            VisibilityType.PARTIAL_VISIBLE -> moment.visibilityList.contains(viewerId)
            VisibilityType.INVISIBLE_TO -> !moment.visibilityList.contains(viewerId)
        }
    }

    private fun isMomentVisibleByTime(visibility: String, createdAt: Long): Boolean {
        return when (visibility) {
            "none" -> false
            "3days" -> createdAt > System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
            "7days" -> createdAt > System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            "30days" -> createdAt > System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            else -> true // "all" 或其他值，永久可見
        }
    }

    /**
     * 將 momentsVisibility 轉換為時間截止點，用於資料庫查詢下推。
     * 回傳 null 表示「全部可見」，Long.MAX_VALUE 表示「不可見」。
     */
    private fun getVisibilityCutoff(visibility: String): Long? {
        return when (visibility) {
            "none" -> Long.MAX_VALUE
            "3days" -> System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
            "7days" -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            "30days" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            else -> null // "all" 或其他值，永久可見
        }
    }

    /**
     * 批量版本 — 消除 N+1 查詢
     * 一次性預取所有動態的 media/likes/comments/users，再批量組裝 DTO。
     */
    private fun mapToMomentDtos(currentUserId: String, moments: List<Moment>): List<MomentDto> {
        if (moments.isEmpty()) return emptyList()

        val momentIds = moments.mapNotNull { it.id }

        // 1. 批量查詢所有關聯數據（各一次查詢）
        val allMedias = momentMediaRepository.findByMomentIdIn(momentIds).groupBy { it.momentId }
        val allLikes = momentLikeRepository.findByMomentIdIn(momentIds).groupBy { it.momentId }
        val allComments = momentCommentRepository.findByMomentIdIn(momentIds).groupBy { it.momentId }

        // 2. 收集所有需要查詢的用戶 ID
        val allUserIds = mutableSetOf<String>()
        moments.forEach { allUserIds.add(it.userId) }
        allLikes.values.flatten().forEach { allUserIds.add(it.userId) }
        allComments.values.flatten().forEach { comment ->
            allUserIds.add(comment.userId)
            comment.replyToUserId?.let { allUserIds.add(it) }
        }

        // 3. 批量查詢用戶（走快取）
        val usersMap = userCacheService.getUsers(allUserIds.toList())

        // 4. 取當前用戶的好友列表（走快取）
        val myFriends = friendshipCacheService.getFriendIds(currentUserId).toSet()
        val visibleUserIds = myFriends + currentUserId

        // 5. 批量查詢當前用戶對所有相關用戶的備註（remark 優先於 displayName）
        val remarkMap = friendshipRepository.findByUserId(currentUserId)
            .filter { it.remark.isNotBlank() }
            .associate { it.friendId to it.remark }

        // 6. 組裝 DTO
        return moments.map { moment ->
            val publisher = usersMap[moment.userId]
            val medias = allMedias[moment.id] ?: emptyList()
            val likes = allLikes[moment.id] ?: emptyList()
            val comments = allComments[moment.id] ?: emptyList()

            val visibleLikes = likes.filter { (visibleUserIds + moment.userId).contains(it.userId) }
            val visibleComments = buildVisibleCommentChain(comments, visibleUserIds + moment.userId)

            MomentDto(
                id = moment.id!!,
                userId = moment.userId,
                userAvatarUrl = publisher?.avatarUrl ?: "",
                userDisplayName = resolveDisplayName(remarkMap[moment.userId], publisher?.displayName),
                content = moment.content,
                location = moment.location,
                visibilityType = moment.visibilityType,
                createdAt = moment.createdAt,
                medias = medias.map { MediaInfo(it.url, it.type, it.sortOrder, it.thumbnailUrl) }.sortedBy { it.sortOrder },
                likes = visibleLikes.map { like ->
                    val likeUser = usersMap[like.userId]
                    LikeDto(like.id!!, like.userId, resolveDisplayName(remarkMap[like.userId], likeUser?.displayName), like.createdAt)
                },
                comments = visibleComments.map { comment ->
                    val commentUser = usersMap[comment.userId]
                    val replyToUser = comment.replyToUserId?.let { usersMap[it] }
                    CommentDto(
                        id = comment.id!!,
                        userId = comment.userId,
                        userDisplayName = resolveDisplayName(remarkMap[comment.userId], commentUser?.displayName),
                        replyToUserId = comment.replyToUserId,
                        replyToUserDisplayName = comment.replyToUserId?.let { replyId ->
                            resolveDisplayName(remarkMap[replyId], replyToUser?.displayName)
                        },
                        content = comment.content,
                        createdAt = comment.createdAt
                    )
                }
            )
        }
    }

    /**
     * 构建可见评论链（双向补链）：
     * 1. 初始可见集合 = 好友 + 动态发布者 + 当前用户的评论
     * 2. 正向补链：如果可见评论 A 回复了不可见用户 C -> C 的评论加入可见集合
     * 3. 反向补链：如果不可见用户 C 回复了可见用户 A -> C 的评论加入可见集合
     * 4. 迭代直到无新增，确保多层链条完整
     */
    private fun buildVisibleCommentChain(
        allComments: List<MomentComment>,
        visibleUserIds: Set<String>
    ): List<MomentComment> {
        // 初始可见集合：好友 + 动态发布者的评论
        val visibleSet = allComments.filter { visibleUserIds.contains(it.userId) }.toMutableSet()

        // 建立 userId -> comments 索引
        val commentsByUserId = allComments.groupBy { it.userId }

        // 迭代双向补链
        var changed = true
        while (changed) {
            changed = false
            val visibleCommentUserIds = visibleSet.map { it.userId }.toSet()

            // 正向：可见评论回复了不可见用户 -> 补充该用户的评论
            val replyToUserIds = visibleSet.mapNotNull { it.replyToUserId }.toSet()
            val forwardMissing = replyToUserIds - visibleCommentUserIds
            for (missingUserId in forwardMissing) {
                val missingComments = commentsByUserId[missingUserId] ?: continue
                val toAdd = missingComments.filter { it !in visibleSet }
                if (toAdd.isNotEmpty()) {
                    visibleSet.addAll(toAdd)
                    changed = true
                }
            }

            // 反向：不可见用户回复了可见用户 -> 补充该不可见用户的评论
            for (comment in allComments) {
                if (comment in visibleSet) continue
                val replyTo = comment.replyToUserId ?: continue
                if (visibleCommentUserIds.contains(replyTo)) {
                    visibleSet.add(comment)
                    changed = true
                }
            }
        }

        // 保持原始排序
        return allComments.filter { it in visibleSet }
    }

    /**
     * 解析显示名称：优先使用备注名，其次使用昵称，兜底 "Unknown"。
     */
    private fun resolveDisplayName(remark: String?, displayName: String?): String {
        if (!remark.isNullOrBlank()) return remark
        if (!displayName.isNullOrBlank()) return displayName
        return "Unknown"
    }
}
