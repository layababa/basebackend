package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AuthResponse
import com.layababateam.xinxiwang_backend.dto.UserDto
import com.layababateam.xinxiwang_backend.dto.UserSummaryDto
import com.layababateam.xinxiwang_backend.dto.UserUpdateRequest

/**
 * 用户资料 HTTP 能力端口。
 *
 * SDK 复用 `/api/user` 路由，资料持久化、缓存失效和好友通知由接入方实现。
 */
interface UserProfilePort {
    fun updateProfile(userId: String, request: UserUpdateRequest): AuthResponse

    fun searchUsers(requesterId: String, keyword: String): List<UserSummaryDto>

    fun getUserById(requesterId: String, targetUserId: String): UserDto?
}
