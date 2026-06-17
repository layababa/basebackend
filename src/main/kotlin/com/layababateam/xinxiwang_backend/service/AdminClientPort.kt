package com.layababateam.xinxiwang_backend.service

/**
 * 后台客户端管理端口。
 *
 * SDK 复用后台 HTTP 契约，在线统计、快照来源、版本规则存储和强制下线动作由接入方实现。
 */
interface AdminClientPort {
    fun onlineStats(): Any

    fun onlineTrend(): Any

    fun forceUpdateRules(): Any

    fun upsertForceUpdateRule(request: ForceUpdateRuleRequest): Any

    fun deleteForceUpdateRule(id: String): AdminClientMutationResult

    fun kickOutdatedClients(id: String): AdminClientMutationResult

    fun appVersions(): Any

    fun updateAppVersion(platform: String, request: AppVersionUpdateRequest): AdminClientMutationResult

    fun deleteAppVersion(platform: String): AdminClientMutationResult
}

data class ForceUpdateRuleRequest(
    val platform: String,
    val minVersion: String,
    val enabled: Boolean = true,
    val updateUrl: String? = null,
)

data class AppVersionUpdateRequest(
    val latestVersion: String? = null,
    val buildNumber: Int? = null,
    val downloadUrl: String? = null,
    val releaseNotes: String? = null,
    val forceUpdate: Boolean? = null,
    val minForceVersion: String? = null,
)

data class AdminClientMutationResult(
    val data: Any?,
    val errorMessage: String? = null,
) {
    val success: Boolean
        get() = errorMessage == null

    companion object {
        fun ok(data: Any?): AdminClientMutationResult = AdminClientMutationResult(data = data)

        fun error(message: String): AdminClientMutationResult =
            AdminClientMutationResult(data = null, errorMessage = message)
    }
}
