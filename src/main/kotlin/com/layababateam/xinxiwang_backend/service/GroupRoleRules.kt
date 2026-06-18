package com.layababateam.xinxiwang_backend.service

/**
 * 群角色纯规则。
 *
 * 业务侧负责读取 Conversation；这里仅判断成员、群主和管理员身份。
 */
object GroupRoleRules {
    fun isMember(members: Collection<String>, userId: String): Boolean =
        members.contains(userId)

    fun isOwner(ownerId: String?, userId: String): Boolean =
        ownerId == userId

    fun isAdminOrOwner(
        adminIds: Collection<String>,
        ownerId: String?,
        userId: String,
    ): Boolean = isOwner(ownerId, userId) || adminIds.contains(userId)
}
