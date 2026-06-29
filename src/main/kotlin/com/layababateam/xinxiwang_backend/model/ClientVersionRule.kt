package com.layababateam.xinxiwang_backend.model

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
        private const val DEFAULT_URL = "https://qwer.iopbcz.cn"
        private const val IOS_APPSTORE_URL = "https://apps.apple.com/app/rentmsg/id6740877498"

        fun getUpdateUrl(platform: String, customUrl: String? = null): String {
            if (!customUrl.isNullOrBlank()) return customUrl
            return if (platform == "ios") IOS_APPSTORE_URL else DEFAULT_URL
        }

        /**
         * Compare two version strings by build number only (the `+N` suffix).
         * Kept for legacy callers; new policy decisions use ClientVersionRules.
         */
        fun compareVersions(v1: String, v2: String): Int {
            val (_, build1) = parseVersion(v1)
            val (_, build2) = parseVersion(v2)
            return build1 - build2
        }

        private fun parseVersion(version: String): Pair<List<Int>, Int> {
            val parts = version.split("+", limit = 2)
            val semver = parts[0].split(".").mapNotNull { it.toIntOrNull() }
            val build = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return semver to build
        }
    }
}

data class ClientUpdateUrlDefaults(
    val defaultUrl: String,
    val iosAppStoreUrl: String,
)
