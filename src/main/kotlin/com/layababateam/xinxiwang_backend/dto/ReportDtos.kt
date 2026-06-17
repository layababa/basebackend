package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SubmitReportRequest(
    @field:NotBlank(message = "目標識別碼不可為空白")
    val targetId: String,

    @field:NotBlank(message = "目標類型不可為空白")
    @field:Pattern(regexp = "^(USER|MESSAGE)$", message = "目標類型必須為 USER 或 MESSAGE")
    val targetType: String,

    @field:NotBlank(message = "檢舉類別不可為空白")
    @field:Size(max = 50, message = "檢舉類別不可超過 50 個字元")
    val category: String,

    @field:Size(max = 1000, message = "描述不可超過 1000 個字元")
    val description: String = ""
)

data class UpdateReportRequest(
    @field:NotBlank(message = "狀態不可為空白")
    @field:Pattern(regexp = "^(RESOLVED|REJECTED)$", message = "狀態必須為 RESOLVED 或 REJECTED")
    val status: String,

    @field:Size(max = 500, message = "管理員備註不可超過 500 個字元")
    val adminNote: String = ""
)

data class ReportDto(
    val id: String,
    val reporterId: String,
    val targetId: String,
    val targetType: String,
    val category: String,
    val description: String,
    val status: String,
    val adminNote: String,
    val createdAt: Long,
    val resolvedAt: Long?
)
