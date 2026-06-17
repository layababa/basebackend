package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

enum class MediaDecryptPolicyScope {
    USER,
    PLATFORM_VERSION,
}

@Document(collection = "media_decrypt_policies")
@CompoundIndexes(
    CompoundIndex(name = "scope_user_idx", def = "{'scope': 1, 'userId': 1}"),
    CompoundIndex(name = "scope_platform_idx", def = "{'scope': 1, 'platform': 1}"),
)
data class MediaDecryptPolicy(
    @Id val id: String? = null,
    @Indexed val scope: MediaDecryptPolicyScope,
    val enabled: Boolean = true,
    val backendDecryptEnabled: Boolean,
    @Indexed val userId: String? = null,
    @Indexed val platform: String? = null,
    val minClientVersion: String? = null,
    val maxClientVersion: String? = null,
    val priority: Int = 0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
