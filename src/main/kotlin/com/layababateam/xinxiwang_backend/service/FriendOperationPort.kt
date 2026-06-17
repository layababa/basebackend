package com.layababateam.xinxiwang_backend.service

data class FriendAcceptResult(
    val friendId: String,
    val conversationId: String,
)

interface FriendOperationPort {
    fun submitFriendRequest(
        fromUserId: String,
        toUserId: String,
        message: String,
        fromGroupId: String?,
        sourceCardMessageId: String?,
    ): String

    fun acceptFriendRequest(requestId: String, userId: String): FriendAcceptResult

    fun rejectFriendRequest(requestId: String, userId: String)

    fun deleteFriend(userId: String, friendId: String)

    fun blockFriend(userId: String, friendId: String)

    fun unblockFriend(userId: String, friendId: String)
}
