package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ClientUpdateUrlDefaults

/**
 * 客户端版本纯规则。
 *
 * SDK 只处理版本比较和更新地址兜底；接入方负责版本记录的存储、
 * 平台白名单和具体强更策略。
 */
object ClientVersionRules {
    fun resolveUpdateUrl(platform: String, customUrl: String?, defaults: ClientUpdateUrlDefaults): String {
        if (!customUrl.isNullOrBlank()) return customUrl
        return if (platform == IOS_PLATFORM) defaults.iosAppStoreUrl else defaults.defaultUrl
    }

    fun compareVersions(v1: String, v2: String): Int =
        buildNumber(v1).compareTo(buildNumber(v2))

    fun versionInRange(version: String, minVersion: String?, maxVersion: String?): Boolean {
        val min = minVersion?.trim()?.takeIf { it.isNotEmpty() }
        val max = maxVersion?.trim()?.takeIf { it.isNotEmpty() }
        if (min != null && compareVersions(version, min) < 0) return false
        if (max != null && compareVersions(version, max) > 0) return false
        return true
    }

    private fun buildNumber(version: String): Int =
        version.split("+", limit = 2).getOrNull(1)?.toIntOrNull() ?: 0

    private const val IOS_PLATFORM = "ios"
}
