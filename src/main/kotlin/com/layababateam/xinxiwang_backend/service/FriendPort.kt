package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.FriendDto
import com.layababateam.xinxiwang_backend.dto.FriendRequestDto
import com.layababateam.xinxiwang_backend.dto.FriendSyncResponse

/**
 * 好友 HTTP 能力端口。
 *
 * SDK 复用好友路由和 DTO，好友关系写入、系统会话 fallback 与通知由接入方实现。
 */
interface FriendPort {
    fun syncFriends(userId: String, afterVersion: Long, limit: Int): FriendSyncResponse

    fun checkIsFriend(userId: String, targetUserId: String): Map<String, Any?>

    fun getFriendList(userId: String): List<FriendDto>

    fun getPendingRequests(userId: String): List<FriendRequestDto>

    fun getAllRequests(userId: String): List<FriendRequestDto>

    fun sendFriendRequest(
        userId: String,
        targetUserId: String,
        message: String,
        fromGroupId: String?,
        sourceCardMessageId: String?,
    ): Map<String, Any?>

    fun acceptFriendRequest(userId: String, requestId: String): Map<String, Any?>

    fun rejectFriendRequest(userId: String, requestId: String, permanent: Boolean)

    fun deleteFriend(userId: String, friendId: String)

    fun blockFriend(userId: String, friendId: String)

    fun unblockFriend(userId: String, friendId: String)
}
