package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class UpdateGlobalPrivacyRequest(
    @field:NotBlank(message = "可見範圍不可為空白")
    @field:Pattern(regexp = "^(all|none|3days|7days|30days)$", message = "可见范围必须为 all、none、3days、7days 或 30days")
    val momentsVisibility: String
)

data class UpdateRelationSettingRequest(
    @field:NotBlank(message = "目標使用者識別碼不可為空白")
    val targetUserId: String,

    val hideMyMoments: Boolean,
    val hideHisMoments: Boolean
)

data class RelationSettingDto(
    val targetUserId: String,
    val hideMyMoments: Boolean,
    val hideHisMoments: Boolean
)
