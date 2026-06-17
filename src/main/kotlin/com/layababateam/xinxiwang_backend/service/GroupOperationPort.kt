package com.layababateam.xinxiwang_backend.service

/**
 * 群操作 WebSocket 能力契约。
 *
 * SDK 负责协议解析和响应格式，业务侧负责群权限、成员关系、落库和通知。
 */
interface GroupOperationPort {
    fun createGroup(
        operatorId: String,
        name: String,
        avatarUrl: String?,
        memberIds: List<String>,
        joinMode: Int,
        maxMembers: Int,
    ): CreatedGroupResult

    fun inviteMembers(operatorId: String, conversationId: String, memberIds: List<String>)

    fun kickMember(operatorId: String, conversationId: String, targetId: String)

    fun kickBatchInactive(operatorId: String, conversationId: String, userIds: List<String>)

    fun quitGroup(operatorId: String, conversationId: String)

    fun disbandGroup(operatorId: String, conversationId: String)

    fun transferOwner(operatorId: String, conversationId: String, newOwnerId: String)

    fun updateGroupInfo(operatorId: String, conversationId: String, name: String?, avatarUrl: String?)

    fun setAnnouncement(operatorId: String, conversationId: String, content: String)

    fun deleteAnnouncement(operatorId: String, conversationId: String, announcementId: String)

    fun setMuteAll(operatorId: String, conversationId: String, mute: Boolean)

    fun muteMember(operatorId: String, conversationId: String, targetId: String, mute: Boolean)

    fun setBlockLinks(operatorId: String, conversationId: String, block: Boolean)

    fun setAddFriendMode(operatorId: String, conversationId: String, mode: Int)

    fun setJoinMode(operatorId: String, conversationId: String, mode: Int)

    fun setSearchable(operatorId: String, conversationId: String, value: Boolean)

    fun setHistoryVisible(operatorId: String, conversationId: String, value: Boolean)

    fun setMaxMembers(operatorId: String, conversationId: String, max: Int)

    fun setAdmin(operatorId: String, conversationId: String, targetId: String, isAdmin: Boolean)

    fun updateMyNickname(operatorId: String, conversationId: String, nickname: String?)

    fun updateGroupRemark(operatorId: String, conversationId: String, remark: String?)

    fun saveToContacts(operatorId: String, conversationId: String, save: Boolean)

    fun getGroupSettings(conversationId: String): Map<String, Any?>

    fun getGroupMembers(conversationId: String): List<Map<String, Any?>>

    fun getGroupReadStatus(conversationId: String): Map<String, Long>

    fun applyJoinGroup(operatorId: String, conversationId: String, message: String)

    fun approveJoinRequest(operatorId: String, requestId: String)

    fun rejectJoinRequest(operatorId: String, requestId: String)

    fun getJoinRequests(operatorId: String, conversationId: String): List<Map<String, Any?>>

    fun searchGroups(keyword: String): List<Map<String, Any?>>

    fun pinMessage(operatorId: String, conversationId: String, messageId: String)

    fun unpinMessage(operatorId: String, conversationId: String, messageId: String)
}

data class CreatedGroupResult(
    val id: String?,
    val name: String?,
    val avatarUrl: String?,
    val ownerId: String?,
    val adminIds: List<String>,
    val joinMode: Int,
    val maxMembers: Int,
    val muteAll: Boolean,
    val blockLinks: Boolean,
    val memberCount: Int,
)
