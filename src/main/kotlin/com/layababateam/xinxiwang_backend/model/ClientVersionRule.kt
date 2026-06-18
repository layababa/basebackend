package com.layababateam.xinxiwang_backend.model

import com.layababateam.xinxiwang_backend.service.ClientVersionRules
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "client_version_rules")
data class ClientVersionRule(
    @Id val id: String? = null,
    @Indexed(unique = true) val platform: String,
    val minVersion: String,
    val enabled: Boolean = true,
    val updateUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun resolveUpdateUrl(platform: String, customUrl: String?, defaults: ClientUpdateUrlDefaults): String =
            ClientVersionRules.resolveUpdateUrl(platform, customUrl, defaults)

        fun compareVersions(v1: String, v2: String): Int =
            ClientVersionRules.compareVersions(v1, v2)
    }
}

data class ClientUpdateUrlDefaults(
    val defaultUrl: String,
    val iosAppStoreUrl: String
)
