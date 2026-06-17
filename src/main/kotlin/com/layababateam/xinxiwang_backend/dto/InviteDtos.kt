package com.layababateam.xinxiwang_backend.dto

data class UserInviteInfo(
    val avatarUrl: String,
    val displayName: String,
    val gender: Int,
    val bio: String,
)

data class GroupInviteInfo(
    val generatorAvatarUrl: String,
    val generatorName: String,
    val groupName: String,
    val groupAvatarUrl: String,
    val memberCount: Int,
    val announcement: String,
)

data class GenerateGroupQrRequest(
    val conversationId: String,
)

data class GenerateGroupQrResponse(
    val encryptedGroupId: String,
)

data class ApplyViaQrRequest(
    val encryptedGroupId: String,
)

data class InviteResult<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
)
