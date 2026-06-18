package com.layababateam.xinxiwang_backend.service

data class LoginSecurityContext(
    val username: String,
    val deviceId: String?,
    val deviceName: String?,
    val platform: String?,
    val clientVersion: String?,
    val metadata: RequestMetadata,
    val method: String,
    val path: String,
)

enum class LoginSecurityAction {
    ALLOW,
    DELAY,
    REJECT,
}

data class LoginSecurityDecision(
    val action: LoginSecurityAction,
    val riskScore: Int,
    val riskLevel: String,
    val reasons: List<String> = emptyList(),
    val delayMs: Long = 0,
) {
    val rejected: Boolean get() = action == LoginSecurityAction.REJECT
}

data class LoginSecurityConfig(
    val enabled: Boolean = true,
    val delayThresholdScore: Int = 45,
    val rejectThresholdScore: Int = 80,
    val maxDelayMs: Long = 1_500,
    val ipFailDelay10m: Long = 20,
    val ipFailBlock10m: Long = 50,
    val userFailDelay15m: Long = 10,
    val userFailBlock15m: Long = 30,
    val userIpFailDelay15m: Long = 5,
    val userIpFailBlock15m: Long = 10,
    val subnetFailAlert10m: Long = 100,
    val subnetFailBlock10m: Long = 300,
    val deviceFailDelay30m: Long = 10,
    val unknownUsernameAlert10m: Long = 20,
    val honeypotIpBlock10m: Long = 3,
    val autoBlockMinutes: Long = 30,
    val honeypotBlockHours: Long = 24,
    val alertFailures5m: Long = 100,
    val alertHoneypot5m: Long = 5,
    val honeyUsernames: Set<String> = setOf("admin", "administrator", "root", "test", "support", "guest"),
)

/**
 * 登录安全公共规则。
 *
 * 业务侧负责计数、封禁持久化和事件落库；SDK 统一维护稳定的归一化和风险辅助规则。
 */
object LoginSecurityRules {
    const val PREFIX = "xinxiwang:loginsec"

    fun counterKey(name: String): String = "$PREFIX:counter:$name"

    fun blockRedisKey(type: String, value: String): String {
        val normalizedType = normalizeBlockType(type)
        return "$PREFIX:block:$normalizedType:${normalizeBlockValue(normalizedType, value)}"
    }

    fun blockLookupKey(type: String, value: String): String {
        val normalizedType = normalizeBlockType(type)
        return "$normalizedType:${normalizeBlockValue(normalizedType, value)}"
    }

    fun normalizeBlockType(type: String): String {
        val normalized = type.trim().uppercase()
        require(normalized in BLOCK_TYPES) { "无效的封禁类型" }
        return normalized
    }

    fun normalizeBlockValue(type: String, value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) { "封禁值不能为空" }
        return if (type == "USERNAME") trimmed.lowercase() else trimmed.take(160)
    }

    fun riskLevel(score: Int): String = when {
        score >= 90 -> "CRITICAL"
        score >= 70 -> "HIGH"
        score >= 45 -> "MEDIUM"
        else -> "LOW"
    }

    fun riskScore(score: Int): Int =
        score.coerceIn(1, 100)

    fun delayMillis(delayMillis: Long, maxMillis: Long = 5_000): Long =
        delayMillis.coerceIn(0, maxMillis)

    fun positiveThreshold(value: Long): Long =
        value.coerceAtLeast(1)

    fun autoBlockMinutes(minutes: Long): Long =
        minutes.coerceIn(1, 24 * 60)

    fun honeypotBlockHours(hours: Long): Long =
        hours.coerceIn(1, 24 * 7)

    fun isSuspiciousUserAgent(userAgent: String?): Boolean {
        if (userAgent.isNullOrBlank()) return true
        val ua = userAgent.lowercase()
        return SUSPICIOUS_USER_AGENT_TOKENS.any { it in ua }
    }

    fun subnetOf(ip: String?): String? {
        return NetworkAddressRules.subnetOf(ip)
    }

    fun usernameHash(username: String): String {
        return HashRules.sha256HexPrefix(username.trim().lowercase(), bytes = 12)
    }

    private val BLOCK_TYPES = setOf("IP", "SUBNET", "DEVICE", "USERNAME")
    private val SUSPICIOUS_USER_AGENT_TOKENS = listOf("curl", "python", "httpclient", "okhttp/3", "go-http-client", "sqlmap", "nikto")
}
