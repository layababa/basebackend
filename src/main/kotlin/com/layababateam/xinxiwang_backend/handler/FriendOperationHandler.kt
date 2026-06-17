package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.FriendOperationPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class FriendRequestHandler(
    private val friendOperationPort: FriendOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "friend_request"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val toUserId = data["toUserId"] as? String
            ?: throw IllegalArgumentException("目标用户ID不能为空")
        val msg = data["message"] as? String ?: ""
        val fromGroupId = data["fromGroupId"] as? String
        val sourceCardMessageId = data["sourceCardMessageId"] as? String
        val requestId = friendOperationPort.submitFriendRequest(
            userId,
            toUserId,
            msg,
            fromGroupId,
            sourceCardMessageId,
        )
        wsResponseSender.send(
            ctx,
            mapOf("type" to "friend_request_sent", "data" to mapOf("requestId" to requestId)),
        )
    }
}

@Component
class FriendAcceptHandler(
    private val friendOperationPort: FriendOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "friend_accept"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String
            ?: throw IllegalArgumentException("请求ID不能为空")
        val friendship = friendOperationPort.acceptFriendRequest(requestId, userId)
        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "friend_accept_success",
                "data" to mapOf(
                    "friendId" to friendship.friendId,
                    "conversationId" to friendship.conversationId,
                ),
            ),
        )
    }
}

@Component
class FriendRejectHandler(
    private val friendOperationPort: FriendOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "friend_reject"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String
            ?: throw IllegalArgumentException("请求ID不能为空")
        friendOperationPort.rejectFriendRequest(requestId, userId)
        wsResponseSender.send(ctx, mapOf("type" to "friend_reject_success"))
    }
}

@Component
class FriendDeleteHandler(
    private val friendOperationPort: FriendOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "friend_delete"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val friendId = data["friendId"] as? String
            ?: throw IllegalArgumentException("好友ID不能为空")
        friendOperationPort.deleteFriend(userId, friendId)
        wsResponseSender.send(ctx, mapOf("type" to "friend_delete_success"))
    }
}

@Component
class FriendBlockHandler(
    private val friendOperationPort: FriendOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "friend_block"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val friendId = data["friendId"] as? String
            ?: throw IllegalArgumentException("好友ID不能为空")
        friendOperationPort.blockFriend(userId, friendId)
        wsResponseSender.send(ctx, mapOf("type" to "friend_block_success"))
    }
}

@Component
class FriendUnblockHandler(
    private val friendOperationPort: FriendOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type = "friend_unblock"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val friendId = data["friendId"] as? String
            ?: throw IllegalArgumentException("好友ID不能为空")
        friendOperationPort.unblockFriend(userId, friendId)
        wsResponseSender.send(ctx, mapOf("type" to "friend_unblock_success"))
    }
}
