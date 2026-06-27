package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomerServiceAccountRequest(
    @field:NotBlank(message = "userId is required")
    val userId: String,
    @field:Size(max = 50, message = "displayName must not exceed 50 characters")
    val displayName: String? = null,
    @field:Size(max = 200, message = "remark must not exceed 200 characters")
    val remark: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

data class CustomerServiceAccountResponse(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val gender: Int,
    val bio: String,
    val remark: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val assignedCount: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CustomerServiceQrCodeRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 80, message = "name must not exceed 80 characters")
    val name: String,
    @field:Size(max = 200, message = "remark must not exceed 200 characters")
    val remark: String? = null,
    val enabled: Boolean = true,
)

data class CustomerServiceQrCodeResponse(
    val id: String,
    val name: String,
    val code: String,
    val qrUrl: String,
    val remark: String?,
    val enabled: Boolean,
    val assignedCount: Long,
    val bindingCount: Int,
    val createdBy: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CustomerServiceQrBindingRequest(
    @field:NotBlank(message = "customerServiceId is required")
    val customerServiceId: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

data class CustomerServiceQrBindingsUpdateRequest(
    val bindings: List<CustomerServiceQrBindingRequest> = emptyList(),
)

data class CustomerServiceQrBindingResponse(
    val id: String,
    val qrCodeId: String,
    val customerServiceId: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val assignedCount: Long,
    val customerService: CustomerServiceAccountResponse?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CustomerServiceQrDetailResponse(
    val qr: CustomerServiceQrCodeResponse,
    val bindings: List<CustomerServiceQrBindingResponse>,
)

data class CustomerServiceQrApplyRequest(
    @field:NotBlank(message = "code is required")
    val code: String,
)

data class CustomerServiceSummary(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val gender: Int,
    val bio: String,
)

data class CustomerServiceQrApplyResponse(
    val alreadyAssigned: Boolean,
    val qrCodeId: String,
    val customerServiceId: String,
    val customerServiceUserId: String,
    val customerService: CustomerServiceSummary,
)
