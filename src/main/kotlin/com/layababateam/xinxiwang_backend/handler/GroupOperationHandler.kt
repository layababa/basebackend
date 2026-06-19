package com.layababateam.xinxiwang_backend.handler

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.service.GroupOperationPort
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class GroupOperationResponseSender(
    private val objectMapper: JsonMapper,
) {
    fun send(ctx: ChannelHandlerContext, data: Map<String, Any?>) {
        ctx.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(data)))
    }
}

@Component
class CreateGroupHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "create_group"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val name = data["name"] as? String ?: throw IllegalArgumentException("名称不能为空")
        val avatarUrl = data["avatarUrl"] as? String
        val memberIds = (data["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val joinMode = (data["joinMode"] as? Number)?.toInt() ?: 0
        val maxMembers = (data["maxMembers"] as? Number)?.toInt() ?: 5000
        val group = groupOperationPort.createGroup(userId, name, avatarUrl, memberIds, joinMode, maxMembers)

        responseSender.send(
            ctx,
            mapOf(
                "type" to "create_group_success",
                "conversationId" to group.id,
                "name" to group.name,
                "avatarUrl" to group.avatarUrl,
                "ownerId" to group.ownerId,
                "adminIds" to group.adminIds,
                "joinMode" to group.joinMode,
                "maxMembers" to group.maxMembers,
                "muteAll" to group.muteAll,
                "blockLinks" to group.blockLinks,
                "memberCount" to group.memberCount,
            ),
        )
    }
}

@Component
class GroupInviteHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_invite"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val memberIds = (data["memberIds"] as? List<*>)?.filterIsInstance<String>()
            ?: throw IllegalArgumentException("成员ID不能为空")
        groupOperationPort.inviteMembers(userId, conversationId, memberIds)
        responseSender.send(ctx, mapOf("type" to "group_invite_success"))
    }
}

@Component
class GroupKickHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_kick"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val targetId = targetId(data)
        groupOperationPort.kickMember(userId, conversationId, targetId)
        responseSender.send(ctx, mapOf("type" to "group_kick_success"))
    }
}

@Component
class GroupKickInactiveHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_kick_inactive"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val userIds = (data["userIds"] as? List<*>)?.filterIsInstance<String>()
            ?: throw IllegalArgumentException("用户ID不能为空")
        groupOperationPort.kickBatchInactive(userId, conversationId, userIds)
        responseSender.send(ctx, mapOf("type" to "group_kick_inactive_success"))
    }
}

@Component
class GroupQuitHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_quit"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        groupOperationPort.quitGroup(userId, conversationId)
        responseSender.send(ctx, mapOf("type" to "group_quit_success", "data" to mapOf("conversationId" to conversationId)))
    }
}

@Component
class GroupDisbandHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_disband"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        groupOperationPort.disbandGroup(userId, conversationId)
        responseSender.send(ctx, mapOf("type" to "group_disband_success", "data" to mapOf("conversationId" to conversationId)))
    }
}

@Component
class GroupTransferOwnerHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_transfer_owner"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val newOwnerId = data["newOwnerId"] as? String ?: throw IllegalArgumentException("新群主ID不能为空")
        groupOperationPort.transferOwner(userId, conversationId, newOwnerId)
        responseSender.send(
            ctx,
            mapOf("type" to "group_transfer_owner_success", "data" to mapOf("conversationId" to conversationId, "newOwnerId" to newOwnerId)),
        )
    }
}

@Component
class GroupUpdateHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_update"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        groupOperationPort.updateGroupInfo(userId, conversationId(data), data["name"] as? String, data["avatarUrl"] as? String)
        responseSender.send(ctx, mapOf("type" to "group_update_success"))
    }
}

@Component
class GroupSetAnnouncementHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_announcement"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        groupOperationPort.setAnnouncement(userId, conversationId(data), data["content"] as? String ?: "")
        responseSender.send(ctx, mapOf("type" to "group_set_announcement_success"))
    }
}

@Component
class GroupDeleteAnnouncementHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_delete_announcement"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val announcementId = data["announcementId"] as? String ?: throw IllegalArgumentException("公告ID不能为空")
        groupOperationPort.deleteAnnouncement(userId, conversationId(data), announcementId)
        responseSender.send(ctx, mapOf("type" to "group_delete_announcement_success"))
    }
}

@Component
class GroupSetMuteAllHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_mute_all"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val mute = data["mute"] as? Boolean ?: throw IllegalArgumentException("静音参数不能为空")
        groupOperationPort.setMuteAll(userId, conversationId(data), mute)
        responseSender.send(ctx, mapOf("type" to "group_set_mute_all_success"))
    }
}

@Component
class GroupMuteMemberHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_mute_member"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val mute = data["mute"] as? Boolean ?: throw IllegalArgumentException("静音参数不能为空")
        groupOperationPort.muteMember(userId, conversationId(data), targetId(data), mute)
        responseSender.send(ctx, mapOf("type" to "group_mute_member_success"))
    }
}

@Component
class GroupSetBlockLinksHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_block_links"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val block = data["block"] as? Boolean ?: throw IllegalArgumentException("封锁参数不能为空")
        groupOperationPort.setBlockLinks(userId, conversationId(data), block)
        responseSender.send(ctx, mapOf("type" to "group_set_block_links_success"))
    }
}

@Component
class GroupSetAddFriendModeHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_add_friend_mode"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val mode = intValue(data, "mode", "模式不能为空")
        groupOperationPort.setAddFriendMode(userId, conversationId(data), mode)
        responseSender.send(ctx, mapOf("type" to "group_set_add_friend_mode_success"))
    }
}

@Component
class GroupSetJoinModeHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_join_mode"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val mode = intValue(data, "mode", "模式不能为空")
        groupOperationPort.setJoinMode(userId, conversationId(data), mode)
        responseSender.send(ctx, mapOf("type" to "group_set_join_mode_success"))
    }
}

@Component
class GroupSetSearchableHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_searchable"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val value = data["value"] as? Boolean ?: throw IllegalArgumentException("值不能为空")
        groupOperationPort.setSearchable(userId, conversationId(data), value)
        responseSender.send(ctx, mapOf("type" to "group_set_searchable_success"))
    }
}

@Component
class GroupSetHistoryVisibleHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_history_visible"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val value = data["value"] as? Boolean ?: throw IllegalArgumentException("值不能为空")
        groupOperationPort.setHistoryVisible(userId, conversationId(data), value)
        responseSender.send(ctx, mapOf("type" to "group_set_history_visible_success"))
    }
}

@Component
class GroupSetMaxMembersHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_max_members"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val max = intValue(data, "max", "最大值不能为空")
        groupOperationPort.setMaxMembers(userId, conversationId(data), max)
        responseSender.send(ctx, mapOf("type" to "group_set_max_members_success"))
    }
}

@Component
class GroupSetAdminHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_set_admin"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val isAdmin = data["isAdmin"] as? Boolean ?: throw IllegalArgumentException("管理员参数不能为空")
        groupOperationPort.setAdmin(userId, conversationId(data), targetId(data), isAdmin)
        responseSender.send(ctx, mapOf("type" to "group_set_admin_success"))
    }
}

@Component
class GroupUpdateMyNicknameHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_update_my_nickname"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        groupOperationPort.updateMyNickname(userId, conversationId(data), data["nickname"] as? String)
        responseSender.send(ctx, mapOf("type" to "group_update_my_nickname_success"))
    }
}

@Component
class GroupUpdateRemarkHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_update_remark"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        groupOperationPort.updateGroupRemark(userId, conversationId(data), data["remark"] as? String)
        responseSender.send(ctx, mapOf("type" to "group_update_remark_success"))
    }
}

@Component
class GroupSaveToContactsHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_save_to_contacts"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val save = data["save"] as? Boolean ?: throw IllegalArgumentException("保存参数不能为空")
        groupOperationPort.saveToContacts(userId, conversationId(data), save)
        responseSender.send(ctx, mapOf("type" to "group_save_to_contacts_success"))
    }
}

@Component
class GetGroupMembersHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "get_group_members"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val settings = groupOperationPort.getGroupSettings(conversationId)
        responseSender.send(
            ctx,
            mapOf(
                "type" to "group_members_response",
                "data" to groupOperationPort.getGroupMembers(conversationId),
                "conversationId" to conversationId,
            ) + settings,
        )
    }
}

@Component
class GetGroupReadStatusHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "get_group_read_status"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        responseSender.send(
            ctx,
            mapOf("type" to "group_read_status_response", "data" to groupOperationPort.getGroupReadStatus(conversationId), "conversationId" to conversationId),
        )
    }
}

@Component
class GroupApplyJoinHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_apply_join"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        groupOperationPort.applyJoinGroup(userId, conversationId(data), data["message"] as? String ?: "")
        responseSender.send(ctx, mapOf("type" to "group_apply_join_success"))
    }
}

@Component
class GroupApproveJoinHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_approve_join"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String ?: throw IllegalArgumentException("请求ID不能为空")
        groupOperationPort.approveJoinRequest(userId, requestId)
        responseSender.send(ctx, mapOf("type" to "group_approve_join_success"))
    }
}

@Component
class GroupRejectJoinHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "group_reject_join"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String ?: throw IllegalArgumentException("请求ID不能为空")
        groupOperationPort.rejectJoinRequest(userId, requestId)
        responseSender.send(ctx, mapOf("type" to "group_reject_join_success"))
    }
}

@Component
class GetJoinRequestsHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "get_join_requests"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        responseSender.send(ctx, mapOf("type" to "join_requests_response", "data" to groupOperationPort.getJoinRequests(userId, conversationId)))
    }
}

@Component
class SearchGroupsHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "search_groups"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val keyword = data["keyword"] as? String ?: ""
        responseSender.send(ctx, mapOf("type" to "search_groups_response", "data" to groupOperationPort.searchGroups(keyword)))
    }
}

@Component
class PinMessageHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "pin_message"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val messageId = messageId(data)
        groupOperationPort.pinMessage(userId, conversationId, messageId)
        responseSender.send(ctx, mapOf("type" to "pin_message_success"))
    }
}

@Component
class UnpinMessageHandler(
    private val groupOperationPort: GroupOperationPort,
    private val responseSender: GroupOperationResponseSender,
) : MessageHandler {
    override val type = "unpin_message"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = conversationId(data)
        val messageId = messageId(data)
        groupOperationPort.unpinMessage(userId, conversationId, messageId)
        responseSender.send(ctx, mapOf("type" to "unpin_message_success"))
    }
}

private fun conversationId(data: Map<String, Any?>): String =
    data["conversationId"] as? String ?: throw IllegalArgumentException("会话ID不能为空")

private fun targetId(data: Map<String, Any?>): String =
    data["targetId"] as? String ?: throw IllegalArgumentException("目标ID不能为空")

private fun messageId(data: Map<String, Any?>): String =
    data["messageId"] as? String ?: throw IllegalArgumentException("消息ID不能为空")

private fun intValue(data: Map<String, Any?>, key: String, errorMessage: String): Int =
    (data[key] as? Number)?.toInt() ?: throw IllegalArgumentException(errorMessage)
