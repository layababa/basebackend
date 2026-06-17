package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SubmitFeedbackRequest(
    @field:NotBlank(message = "回饋內容不可為空白")
    @field:Size(max = 2000, message = "回饋內容不可超過 2000 個字元")
    val content: String,

    @field:Size(max = 9, message = "最多上傳 9 張圖片")
    val images: List<String> = emptyList()
)

data class UpdateFeedbackRequest(
    @field:NotBlank(message = "狀態不可為空白")
    @field:Pattern(regexp = "^(RESOLVED|REJECTED)$", message = "狀態必須為 RESOLVED 或 REJECTED")
    val status: String,

    @field:Size(max = 500, message = "管理員備註不可超過 500 個字元")
    val adminNote: String = ""
)

data class FeedbackDto(
    val id: String,
    val userId: String,
    val content: String,
    val status: String,
    val adminNote: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val images: List<String> = emptyList()
)
