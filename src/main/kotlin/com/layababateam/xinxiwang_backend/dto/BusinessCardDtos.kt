package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.model.BusinessCard
import com.layababateam.xinxiwang_backend.model.CustomField
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateBusinessCardRequest(
    @field:NotBlank(message = "姓名不可為空白")
    @field:Size(max = 50, message = "姓名不可超過 50 個字元")
    val name: String,

    @field:Size(max = 50, message = "職稱不可超過 50 個字元")
    val title: String? = null,

    @field:Size(max = 100, message = "公司名稱不可超過 100 個字元")
    val company: String? = null,

    @field:Size(max = 20, message = "電話號碼不可超過 20 個字元")
    val phone: String? = null,

    @field:Email(message = "電子郵件格式不正確")
    @field:Size(max = 100, message = "電子郵件不可超過 100 個字元")
    val email: String? = null,

    @field:Size(max = 200, message = "地址不可超過 200 個字元")
    val address: String? = null,

    @field:Size(max = 500, message = "頭像連結不可超過 500 個字元")
    val avatarUrl: String? = null,

    @field:Size(max = 200, message = "網站連結不可超過 200 個字元")
    val website: String? = null,

    @field:Size(max = 10, message = "自訂欄位不可超過 10 個")
    @field:Valid
    val customFields: List<CustomField> = emptyList(),

    val isDefault: Boolean = false
)

data class UpdateBusinessCardRequest(
    @field:Size(max = 50, message = "姓名不可超過 50 個字元")
    val name: String? = null,

    @field:Size(max = 50, message = "職稱不可超過 50 個字元")
    val title: String? = null,

    @field:Size(max = 100, message = "公司名稱不可超過 100 個字元")
    val company: String? = null,

    @field:Size(max = 20, message = "電話號碼不可超過 20 個字元")
    val phone: String? = null,

    @field:Email(message = "電子郵件格式不正確")
    @field:Size(max = 100, message = "電子郵件不可超過 100 個字元")
    val email: String? = null,

    @field:Size(max = 200, message = "地址不可超過 200 個字元")
    val address: String? = null,

    @field:Size(max = 500, message = "頭像連結不可超過 500 個字元")
    val avatarUrl: String? = null,

    @field:Size(max = 200, message = "網站連結不可超過 200 個字元")
    val website: String? = null,

    @field:Size(max = 10, message = "自訂欄位不可超過 10 個")
    @field:Valid
    val customFields: List<CustomField>? = null,

    val isDefault: Boolean? = null
)


data class BusinessCardResponse(
    val id: String,
    val userId: String,
    val name: String,
    val title: String?,
    val company: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val avatarUrl: String?,
    val website: String?,
    val customFields: List<CustomField>,
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromModel(model: BusinessCard) = BusinessCardResponse(
            id = model.id!!,
            userId = model.userId,
            name = model.name,
            title = model.title,
            company = model.company,
            phone = model.phone,
            email = model.email,
            address = model.address,
            avatarUrl = model.avatarUrl,
            website = model.website,
            customFields = model.customFields,
            isDefault = model.isDefault,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt
        )
    }
}
