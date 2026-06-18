package com.layababateam.xinxiwang_backend.service

interface FriendAcceptConsumerPort {
    fun acceptFriend(event: FriendAcceptEvent)
}

data class FriendAcceptEvent(
    val fromUserId: String,
    val toUserId: String,
    val conversationId: String,
)
