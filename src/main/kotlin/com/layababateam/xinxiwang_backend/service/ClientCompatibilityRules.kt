package com.layababateam.xinxiwang_backend.service

data class ClientFingerprint(
    val backendCompatVersion: Int,
    val platform: String,
    val source: String,
)

/**
 * 客户端兼容策略纯规则。
 *
 * 接入方负责从请求、token 或设备会话里提取原始字段，SDK 统一维护
 * 兼容号解析、默认值和 legacy 分支判定。
 */
object ClientCompatibilityRules {
    const val UNKNOWN_VERSION = "unknown"
    const val UNKNOWN_BACKEND_COMPAT_VERSION = 0
    const val MEETING_SCHEDULE_UPDATE_MESSAGE = "请更新客户端后使用预约宣讲会"

    fun fingerprint(
        backendCompatVersion: String?,
        platform: String?,
        source: String,
    ): ClientFingerprint =
        ClientFingerprint(
            backendCompatVersion = parseCompatVersion(backendCompatVersion) ?: UNKNOWN_BACKEND_COMPAT_VERSION,
            platform = normalize(platform),
            source = source,
        )

    fun normalize(value: String?): String =
        StringValueRules.nonBlank(value)
            ?.takeIf { it != "null" }
            ?: UNKNOWN_VERSION

    fun parseCompatVersion(value: String?): Int? =
        StringValueRules.nonBlank(value)?.toIntOrNull()?.coerceAtLeast(0)

    fun backendCompatVersion(value: Int): Int =
        value.coerceAtLeast(UNKNOWN_BACKEND_COMPAT_VERSION)

    fun useLegacyFeature(
        backendCompatVersion: Int,
        minBackendCompatVersion: Int,
        legacyEnabled: Boolean,
    ): Boolean {
        if (!legacyEnabled) return false
        return backendCompatVersion < minBackendCompatVersion
    }
}
