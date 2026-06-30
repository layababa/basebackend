package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "用户名不能为空")
    @field:Size(min = 3, max = 20, message = "用户名长度必须介于 3 到 20 之间")
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字和下划线")
    val username: String,

    @field:Size(max = 30, message = "显示名称不能超过 30 个字符")
    val displayName: String?,

    @field:Size(max = 500, message = "头像链接不能超过 500 个字符")
    val avatarUrl: String?,

    @field:Min(value = 0, message = "性别值最小为 0")
    @field:Max(value = 2, message = "性别值最大为 2")
    val gender: Int?,

    @field:Size(max = 100, message = "个人简介不能超过 100 个字符")
    val bio: String?,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 6, max = 50, message = "密码长度必须介于 6 到 50 之间")
    val password:  String,

    @field:Size(max = 20, message = "邀请码不能超过 20 个字符")
    val inviteCode: String? = null
)

data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    @field:Size(max = 20, message = "用户名不能超过 20 个字符")
    val username: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(max = 50, message = "密码不能超过 50 个字符")
    val password: String,

    @field:Size(max = 50, message = "设备名称不能超过 50 个字符")
    val deviceName: String? = null,

    @field:Size(max = 20, message = "平台名称不能超过 20 个字符")
    val platform: String? = null,

    @field:Size(max = 100, message = "设备标识码不能超过 100 个字符")
    val deviceId: String? = null,

    @field:Size(max = 50, message = "客户端版本不能超过 50 个字符")
    val clientVersion: String? = null
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserDto? = null
)

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val gender: Int,
    val bio: String,
    val myInviteCode: String,
    val version: Long = 1,
    val momentsBgUrl: String? = null,
    val momentsVisibility: String = "all",
    val isBot: Boolean = false,
    val isOperator: Boolean = false,
    val isCustomerService: Boolean = false
)

data class UserUpdateRequest(
    @field:Size(max = 30, message = "显示名称不能超过 30 个字符")
    val displayName: String?,

    @field:Size(max = 500, message = "头像链接不能超过 500 个字符")
    val avatarUrl: String?,

    val gender: Int?,

    @field:Size(max = 100, message = "个人简介不能超过 100 个字符")
    val bio: String?,

    @field:Size(max = 500, message = "朋友圈背景链接不能超过 500 个字符")
    val momentsBgUrl: String? = null,

    @field:Pattern(regexp = "all|none|3days|7days|30days", message = "朋友圈可见范围无效")
    val momentsVisibility: String? = null
)
