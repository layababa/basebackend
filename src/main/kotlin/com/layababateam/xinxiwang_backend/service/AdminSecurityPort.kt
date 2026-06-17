package com.layababateam.xinxiwang_backend.service

/**
 * 后台登录安全管理端口。
 *
 * SDK 复用后台 HTTP 契约，登录安全事件、告警、封禁和配置读写由接入方实现。
 */
interface AdminSecurityPort {
    fun summary(): Any

    fun events(query: LoginSecurityEventQuery): Any

    fun alerts(query: LoginSecurityAlertQuery): Any

    fun ackAlert(id: String, adminId: String?): AdminSecurityMutationResult

    fun resolveAlert(id: String, adminId: String?): AdminSecurityMutationResult

    fun blocks(query: LoginSecurityBlockQuery): Any

    fun createBlock(request: LoginSecurityBlockRequest, adminId: String?): AdminSecurityMutationResult

    fun revokeBlock(id: String, adminId: String?): AdminSecurityMutationResult

    fun config(): Any

    fun updateConfig(request: LoginSecurityConfigUpdateRequest): AdminSecurityMutationResult
}

data class LoginSecurityEventQuery(
    val page: Int,
    val size: Int,
    val eventType: String?,
    val username: String?,
    val userId: String?,
    val ip: String?,
    val deviceId: String?,
    val riskLevel: String?,
    val startAt: Long?,
    val endAt: Long?,
)

data class LoginSecurityAlertQuery(
    val page: Int,
    val size: Int,
    val status: String?,
    val alertType: String?,
)

data class LoginSecurityBlockQuery(
    val page: Int,
    val size: Int,
    val active: Boolean?,
    val type: String?,
    val value: String?,
)

data class LoginSecurityBlockRequest(
    val type: String,
    val value: String,
    val reason: String,
    val expiresAt: Long? = null,
)

data class LoginSecurityConfigUpdateRequest(
    val values: Map<String, String>,
)

data class AdminSecurityMutationResult(
    val data: Any? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val notFound: Boolean = false,
) {
    val success: Boolean
        get() = errorMessage == null

    companion object {
        fun ok(data: Any? = null, message: String? = null): AdminSecurityMutationResult =
            AdminSecurityMutationResult(data = data, message = message)

        fun invalid(message: String): AdminSecurityMutationResult =
            AdminSecurityMutationResult(errorMessage = message)

        fun notFound(message: String): AdminSecurityMutationResult =
            AdminSecurityMutationResult(errorMessage = message, notFound = true)
    }
}
