package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "web_customer_service_entries")
@CompoundIndexes(
    CompoundIndex(name = "wcs_entries_enabled_created_at_idx", def = "{ 'enabled': 1, 'createdAt': -1 }"),
)
data class WebCustomerServiceEntry(
    @Id
    val id: String? = null,
    val name: String,
    @Indexed
    val enabled: Boolean = true,
    val allowedDomains: List<String>,
    val seatAdminIds: List<String>,
    val welcomeMessage: String = "",
    val themeColor: String = "#2563eb",
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Document(collection = "web_customer_service_sessions")
@CompoundIndexes(
    CompoundIndex(name = "wcs_sessions_entry_status_last_msg_idx", def = "{ 'entryId': 1, 'status': 1, 'lastMessageAt': -1 }"),
    CompoundIndex(name = "wcs_sessions_entry_visitor_status_idx", def = "{ 'entryId': 1, 'visitorId': 1, 'status': 1 }"),
    CompoundIndex(name = "wcs_sessions_assignee_status_last_msg_idx", def = "{ 'assignedAdminId': 1, 'status': 1, 'lastMessageAt': -1 }"),
)
data class WebCustomerServiceSession(
    @Id
    val id: String? = null,
    @Indexed
    val entryId: String,
    val visitorId: String,
    val visitorName: String? = null,
    val visitorPhone: String? = null,
    val visitorEmail: String? = null,
    val sourceUrl: String? = null,
    val referrer: String? = null,
    val userAgent: String? = null,
    val externalApiCredentialId: String? = null,
    val externalAnonymousId: String? = null,
    val salesmartlyChannel: Int = 3,
    val salesmartlyChannelUid: String = "",
    val salesmartlyRemark: String? = null,
    val salesmartlyTagsJson: String = "[]",
    val status: WebCustomerServiceSessionStatus = WebCustomerServiceSessionStatus.WAITING,
    val assignedAdminId: String? = null,
    val assignedAdminUsername: String? = null,
    val lastMessagePreview: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Document(collection = "web_customer_service_messages")
@CompoundIndexes(
    CompoundIndex(name = "wcs_messages_session_created_idx", def = "{ 'sessionId': 1, 'createdAt': 1 }"),
)
data class WebCustomerServiceMessage(
    @Id
    val id: String? = null,
    @Indexed
    val entryId: String,
    @Indexed
    val sessionId: String,
    val senderType: WebCustomerServiceSenderType,
    val senderId: String? = null,
    val senderName: String? = null,
    val contentType: WebCustomerServiceContentType = WebCustomerServiceContentType.TEXT,
    val content: String = "",
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class WebCustomerServiceSessionStatus {
    WAITING,
    ACTIVE,
    CLOSED,
}

enum class WebCustomerServiceSenderType {
    VISITOR,
    ADMIN,
    SYSTEM,
}

enum class WebCustomerServiceContentType {
    TEXT,
    IMAGE,
}
