package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.GenerateGroupQrResponse
import com.layababateam.xinxiwang_backend.dto.GroupInviteInfo
import com.layababateam.xinxiwang_backend.dto.InviteResult
import com.layababateam.xinxiwang_backend.dto.UserInviteInfo

/**
 * 邀请页与群二维码能力。
 *
 * SDK 复用 HTTP 契约，二维码加解密、群权限和入群申请由接入方实现。
 */
interface InvitePort {
    fun getUserInviteInfo(userId: String): InviteResult<UserInviteInfo>

    fun getGroupInviteInfo(encryptedGroupId: String): InviteResult<GroupInviteInfo>

    fun generateGroupQr(userId: String, conversationId: String): InviteResult<GenerateGroupQrResponse>

    fun applyViaQr(userId: String, encryptedGroupId: String): InviteResult<Any>
}
