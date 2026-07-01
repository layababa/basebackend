package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomerServiceExternalApiCredentialRequest(
    @field:NotBlank
    @field:Size(max = 80)
    val name: String,
    @field:NotBlank
    val qrCodeId: String,
    val enabled: Boolean = true,
)

data class CustomerServiceExternalApiCredentialResponse(
    val id: String,
    val name: String,
    val apiKey: String,
    val qrCodeId: String,
    val enabled: Boolean,
    val createdBy: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CustomerServiceExternalApiCredentialSecretResponse(
    val credential: CustomerServiceExternalApiCredentialResponse,
    val apiSecret: String,
)

data class ExternalCustomerServiceApiResponse<T>(
    val code: Int,
    val data: T? = null,
    val msg: String,
    @get:JsonProperty("request_id")
    val requestId: String,
)

data class ExternalCustomerServiceCreateSessionRequest(
    @field:NotBlank
    @field:Size(max = 128)
    val anonymousId: String,
    @field:Size(max = 80)
    val visitorName: String? = null,
    @field:Size(max = 1000)
    val sourceUrl: String? = null,
    @field:NotBlank
    @field:Size(max = 5000)
    val content: String,
)

data class ExternalCustomerServiceTextMessageRequest(
    @field:NotBlank
    @field:Size(max = 5000)
    val content: String,
)

data class ExternalCustomerServiceInfo(
    val customerServiceId: String,
    val customerServiceUserId: String,
    val displayName: String,
    val avatarUrl: String,
    val bio: String,
)

data class ExternalCustomerServiceSessionResult(
    val session: WebCustomerServiceSessionResponse,
    val customerService: ExternalCustomerServiceInfo,
    val message: WebCustomerServiceMessageResponse,
)

data class ExternalCustomerServiceMessagesResult(
    val session: WebCustomerServiceSessionResponse,
    val messages: List<WebCustomerServiceMessageResponse>,
)
