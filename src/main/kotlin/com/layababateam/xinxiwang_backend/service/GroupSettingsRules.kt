package com.layababateam.xinxiwang_backend.service

/**
 * 群设置纯规则。
 *
 * 业务侧负责读取和写入群设置；SDK 只维护客户端兼容值、有效范围等稳定规则。
 */
object GroupSettingsRules {
    const val ADD_FRIEND_ALL = 0
    const val ADD_FRIEND_OWNER_ONLY = 1
    const val ADD_FRIEND_ADMIN_ONLY = 2

    /**
     * 旧版“仅群成员”选项已废弃，读写两侧统一回退为“所有人”。
     */
    const val LEGACY_ADD_FRIEND_MEMBER_ONLY = 3

    fun normalizeAddFriendMode(raw: Int): Int =
        if (raw == LEGACY_ADD_FRIEND_MEMBER_ONLY) ADD_FRIEND_ALL else raw

    fun isValidAddFriendMode(raw: Int): Boolean =
        normalizeAddFriendMode(raw) in ADD_FRIEND_ALL..ADD_FRIEND_ADMIN_ONLY
}
