package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

enum class MessageDeliveryMode {
    AUTO,
    FULL_PUSH,
    SIGNAL_PULL,
}

enum class MessageDeliveryPolicyScope {
    USER,
    USER_BATCH,
    PLATFORM_VERSION,
}

@Document(collection = "message_delivery_policies")
data class MessageDeliveryPolicy(
    @Id val id: String? = null,
    val name: String = "",
    val scope: MessageDeliveryPolicyScope,
    val mode: MessageDeliveryMode,
    val enabled: Boolean = true,
    @Indexed val userId: String? = null,
    val userIds: List<String> = emptyList(),
    val platform: String? = null,
    val minClientVersion: String? = null,
    val maxClientVersion: String? = null,
    val priority: Int = 0,
    val note: String? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
