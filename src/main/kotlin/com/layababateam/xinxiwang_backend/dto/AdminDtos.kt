package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// ========== Admin Auth ==========

data class AdminLoginRequest(
    @field:NotBlank(message = "使用者名稱不可為空白")
    @field:Size(max = 20, message = "使用者名稱不可超過 20 個字元")
    val username: String,

    @field:NotBlank(message = "密碼不可為空白")
    @field:Size(max = 50, message = "密碼不可超過 50 個字元")
    val password: String
)

data class AdminVerify2faRequest(
    @field:NotBlank(message = "臨時令牌不可為空白")
    val tempToken: String,

    @field:NotBlank(message = "驗證碼不可為空白")
    @field:Size(min = 6, max = 6, message = "驗證碼必須為 6 位")
    val code: String
)

data class AdminConfirm2faSetupRequest(
    @field:NotBlank(message = "臨時令牌不可為空白")
    val tempToken: String,

    @field:NotBlank(message = "驗證碼不可為空白")
    @field:Size(min = 6, max = 6, message = "驗證碼必須為 6 位")
    val code: String
)

data class AdminConfirmSelf2faSetupRequest(
    @field:NotBlank(message = "驗證碼不可為空白")
    @field:Size(min = 6, max = 6, message = "驗證碼必須為 6 位")
    val code: String,

    @field:Size(min = 6, max = 6, message = "目前驗證碼必須為 6 位")
    val currentCode: String? = null
)

data class AdminRefreshTokenRequest(
    @field:NotBlank(message = "刷新令牌不可為空白")
    val refreshToken: String
)

data class AdminChangePasswordRequest(
    @field:NotBlank(message = "舊密碼不可為空白")
    @field:Size(min = 6, max = 50, message = "舊密碼長度必須介於 6 到 50 之間")
    val oldPassword: String,

    @field:NotBlank(message = "新密碼不可為空白")
    @field:Size(min = 6, max = 50, message = "新密碼長度必須介於 6 到 50 之間")
    val newPassword: String
)

// ========== Admin Management ==========

data class CreateAdminRequest(
    @field:NotBlank(message = "使用者名稱不可為空白")
    @field:Size(min = 3, max = 20, message = "使用者名稱長度必須介於 3 到 20 之間")
    val username: String,

    @field:NotBlank(message = "密碼不可為空白")
    @field:Size(min = 6, max = 50, message = "密碼長度必須介於 6 到 50 之間")
    val password: String,

    @field:NotBlank(message = "角色不可為空白")
    @field:Pattern(regexp = "^(SUPER_ADMIN|ADMIN|MODERATOR)$", message = "角色必須為 SUPER_ADMIN、ADMIN 或 MODERATOR")
    val role: String
)

data class UpdateAdminRoleRequest(
    @field:NotBlank(message = "角色不可為空白")
    @field:Pattern(regexp = "^(SUPER_ADMIN|ADMIN|MODERATOR)$", message = "角色必須為 SUPER_ADMIN、ADMIN 或 MODERATOR")
    val role: String
)

data class UpdateAdminPasswordRequest(
    @field:NotBlank(message = "密碼不可為空白")
    @field:Size(min = 6, max = 50, message = "密碼長度必須介於 6 到 50 之間")
    val password: String
)

data class UpdateAdminStatusRequest(
    val isActive: Boolean
)

// ========== User Management ==========

data class AdminResetPasswordRequest(
    @field:NotBlank(message = "密碼不可為空白")
    @field:Size(min = 6, max = 50, message = "密碼長度必須介於 6 到 50 之間")
    val password: String
)

data class AdminBanUserRequest(
    @field:NotBlank(message = "封禁原因不可為空白")
    @field:Size(max = 500, message = "封禁原因不可超過 500 個字元")
    val reason: String,

    @field:NotBlank(message = "封禁類型不可為空白")
    @field:Pattern(regexp = "^(WARNING|TEMPORARY|PERMANENT)$", message = "類型必須為 WARNING、TEMPORARY 或 PERMANENT")
    val type: String,

    @field:Min(value = 1, message = "封禁時長最小為 1 分鐘")
    val duration: Long? = null
)

// ========== Wallet ==========

data class AdminAdjustBalanceRequest(
    @field:NotBlank(message = "金額不可為空白")
    @field:Pattern(regexp = "^\\d+(\\.\\d{1,2})?$", message = "金額格式錯誤，需為正數且最多兩位小數")
    val amount: String,

    @field:NotBlank(message = "操作類型不可為空白")
    @field:Pattern(regexp = "^(INCREASE|DECREASE)$", message = "類型必須為 INCREASE 或 DECREASE")
    val type: String,

    @field:Size(max = 200, message = "備註不可超過 200 個字元")
    val remark: String = ""
)

// ========== Moderation ==========

data class AdminUpdateReportRequest(
    @field:NotBlank(message = "狀態不可為空白")
    @field:Size(max = 20, message = "狀態不可超過 20 個字元")
    val status: String,

    @field:Size(max = 500, message = "管理員備註不可超過 500 個字元")
    val adminNote: String? = null,

    @field:Size(max = 500, message = "處理結果不可超過 500 個字元")
    val resolution: String? = null,

    @field:Pattern(regexp = "^(WARNING|TEMPORARY|PERMANENT)$", message = "封禁類型必須為 WARNING、TEMPORARY 或 PERMANENT")
    val banType: String? = null,

    @field:Min(value = 1, message = "封禁時長最小為 1 分鐘")
    val banDuration: Long? = null,

    @field:Size(max = 500, message = "管理員評語不可超過 500 個字元")
    val adminRemark: String? = null
)

// ========== Feedback ==========

data class AdminUpdateFeedbackRequest(
    @field:NotBlank(message = "狀態不可為空白")
    @field:Size(max = 20, message = "狀態不可超過 20 個字元")
    val status: String,

    @field:Size(max = 50, message = "指派人不可超過 50 個字元")
    val assignedTo: String? = null,

    @field:Size(max = 500, message = "管理員評語不可超過 500 個字元")
    val adminRemark: String = "",

    @field:jakarta.validation.constraints.Min(value = 0, message = "獎勵積分不可為負數")
    val rewardPoints: Int = 0
)

data class AdminFeedbackReplyRequest(
    @field:NotBlank(message = "回覆內容不可為空白")
    @field:Size(max = 1000, message = "回覆內容不可超過 1000 個字元")
    val content: String
)

// ========== System ==========

data class AdminAddBannedWordRequest(
    @field:NotBlank(message = "違禁詞不可為空白")
    @field:Size(max = 50, message = "違禁詞不可超過 50 個字元")
    val word: String
)

data class AdminBatchAddBannedWordsRequest(
    @field:Size(min = 1, max = 500, message = "批量添加數量必須介於 1 到 500 之間")
    val words: List<@Size(max = 50, message = "每個違禁詞不可超過 50 個字元") String>
)

data class AdminBatchDeleteRequest(
    @field:Size(min = 1, max = 500, message = "批量刪除數量必須介於 1 到 500 之間")
    val ids: List<String>
)
