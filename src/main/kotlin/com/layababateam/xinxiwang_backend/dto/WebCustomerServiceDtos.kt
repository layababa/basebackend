package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.model.WebCustomerServiceContentType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceMessage
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSenderType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class WebCustomerServiceEntryRequest(
    @field:NotBlank
    @field:Size(max = 80)
    val name: String,
    val enabled: Boolean = true,
    val allowedDomains: List<String>,
    val seatAdminIds: List<String> = emptyList(),
    @field:Size(max = 500)
    val welcomeMessage: String = "",
    val themeColor: String = "#2563eb",
)

data class WebCustomerServiceEntryResponse(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val allowedDomains: List<String>,
    val seatAdminIds: List<String>,
    val welcomeMessage: String,
    val themeColor: String,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class WebCustomerServiceBootstrapResponse(
    val entryId: String,
    val name: String,
    val enabled: Boolean,
    val welcomeMessage: String,
    val themeColor: String,
)

data class WebCustomerServiceCreateSessionRequest(
    @field:NotBlank
    @field:Size(max = 128)
    val visitorId: String,
    @field:Size(max = 80)
    val visitorName: String? = null,
    @field:Size(max = 40)
    val visitorPhone: String? = null,
    @field:Size(max = 120)
    val visitorEmail: String? = null,
    @field:Size(max = 1000)
    val sourceUrl: String? = null,
    @field:Size(max = 1000)
    val referrer: String? = null,
)

data class WebCustomerServiceCreateSessionResponse(
    val session: WebCustomerServiceSessionResponse,
    val visitorToken: String,
)

data class WebCustomerServiceSessionResponse(
    val id: String,
    val entryId: String,
    val visitorId: String,
    val visitorName: String?,
    val visitorPhone: String?,
    val visitorEmail: String?,
    val sourceUrl: String?,
    val referrer: String?,
    val userAgent: String?,
    val externalApiCredentialId: String? = null,
    val externalAnonymousId: String? = null,
    val status: WebCustomerServiceSessionStatus,
    val assignedAdminId: String?,
    val assignedAdminUsername: String?,
    val lastMessagePreview: String,
    val lastMessageAt: Long,
    val closedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val entryName: String? = null,
)

data class WebCustomerServiceMessagesResponse(
    val session: WebCustomerServiceSessionResponse,
    val messages: List<WebCustomerServiceMessageResponse>,
)

data class WebCustomerServiceMessageResponse(
    val id: String,
    val entryId: String,
    val sessionId: String,
    val senderType: WebCustomerServiceSenderType,
    val senderId: String?,
    val senderName: String?,
    val contentType: WebCustomerServiceContentType,
    val content: String,
    val imageUrl: String?,
    val createdAt: Long,
)

data class WebCustomerServiceTextMessageRequest(
    @field:NotBlank
    @field:Size(max = 5000)
    val content: String,
)

data class CustomerServiceWorkbenchProfileResponse(
    val customerServiceUserId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val accountId: String,
    val entry: WebCustomerServiceEntryResponse,
)

data class WebCustomerServiceScriptResponse(
    val scriptUrl: String,
    val scriptTag: String,
)

fun WebCustomerServiceSession.toWebCustomerServiceResponse(entryName: String? = null): WebCustomerServiceSessionResponse =
    WebCustomerServiceSessionResponse(
        id = id.orEmpty(),
        entryId = entryId,
        visitorId = visitorId,
        visitorName = visitorName,
        visitorPhone = visitorPhone,
        visitorEmail = visitorEmail,
        sourceUrl = sourceUrl,
        referrer = referrer,
        userAgent = userAgent,
        externalApiCredentialId = externalApiCredentialId,
        externalAnonymousId = externalAnonymousId,
        status = status,
        assignedAdminId = assignedAdminId,
        assignedAdminUsername = assignedAdminUsername,
        lastMessagePreview = lastMessagePreview,
        lastMessageAt = lastMessageAt,
        closedAt = closedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        entryName = entryName,
    )

fun WebCustomerServiceMessage.toWebCustomerServiceResponse(): WebCustomerServiceMessageResponse =
    WebCustomerServiceMessageResponse(
        id = id.orEmpty(),
        entryId = entryId,
        sessionId = sessionId,
        senderType = senderType,
        senderId = senderId,
        senderName = senderName,
        contentType = contentType,
        content = content,
        imageUrl = imageUrl,
        createdAt = createdAt,
    )
