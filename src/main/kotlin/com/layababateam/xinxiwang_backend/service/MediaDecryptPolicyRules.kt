package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicy

data class MediaDecryptRequestContext(
    val userId: String? = null,
    val platform: String? = null,
    val clientVersion: String? = null,
)

/**
 * 媒体后端解密策略纯规则。
 *
 * 接入方负责读取全局开关和策略列表，SDK 统一维护上下文归一化、
 * 版本范围匹配和策略优先级。
 */
object MediaDecryptPolicyRules {
    fun backendDecryptEnabled(
        context: MediaDecryptRequestContext,
        masterEnabled: Boolean,
        defaultEnabled: Boolean,
        userPolicies: List<MediaDecryptPolicy>,
        platformPolicies: List<MediaDecryptPolicy>,
    ): Boolean {
        if (!masterEnabled) return false
        if (!hasPlatformVersionContext(context.platform, context.clientVersion)) return true
        chooseUserPolicy(context.userId, userPolicies)?.let { return it.backendDecryptEnabled }
        choosePlatformVersionPolicy(context.platform, context.clientVersion, platformPolicies)
            ?.let { return it.backendDecryptEnabled }
        return defaultEnabled
    }

    fun chooseUserPolicy(userId: String?, policies: List<MediaDecryptPolicy>): MediaDecryptPolicy? {
        val normalizedUserId = StringValueRules.nonBlank(userId) ?: return null
        return policies.asSequence()
            .filter { it.enabled }
            .filter { it.userId == normalizedUserId }
            .sortedWith(policyOrdering)
            .firstOrNull()
    }

    fun choosePlatformVersionPolicy(
        platform: String?,
        clientVersion: String?,
        policies: List<MediaDecryptPolicy>,
    ): MediaDecryptPolicy? {
        val normalizedPlatform = normalizePlatform(platform) ?: return null
        val normalizedVersion = normalizeClientVersion(clientVersion) ?: return null
        return policies.asSequence()
            .filter { it.enabled }
            .filter { normalizePlatform(it.platform) == normalizedPlatform }
            .filter { versionInRange(normalizedVersion, it.minClientVersion, it.maxClientVersion) }
            .sortedWith(policyOrdering)
            .firstOrNull()
    }

    fun versionInRange(version: String, minVersion: String?, maxVersion: String?): Boolean {
        return ClientVersionRules.versionInRange(version, minVersion, maxVersion)
    }

    fun normalizePlatform(platform: String?): String? =
        StringValueRules.lowerNonBlank(platform)?.takeIf { it != "unknown" }

    fun normalizeClientVersion(clientVersion: String?): String? =
        StringValueRules.nonBlank(clientVersion)?.takeIf { !it.equals("unknown", ignoreCase = true) }

    fun hasPlatformVersionContext(platform: String?, clientVersion: String?): Boolean =
        normalizePlatform(platform) != null && normalizeClientVersion(clientVersion) != null

    private val policyOrdering = compareByDescending<MediaDecryptPolicy> { it.priority }
        .thenByDescending { it.updatedAt }
}
