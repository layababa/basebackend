package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.MediaDecryptConfigResponse
import com.layababateam.xinxiwang_backend.dto.MediaDecryptDefaultRequest
import com.layababateam.xinxiwang_backend.dto.MediaDecryptMasterRequest
import com.layababateam.xinxiwang_backend.dto.MediaDecryptPolicyRequest
import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicy
import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicyScope
import com.layababateam.xinxiwang_backend.service.AdminMediaDecryptPort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import com.layababateam.xinxiwang_backend.service.ClientVersionRules
import com.layababateam.xinxiwang_backend.service.StringValueRules
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/system/media-decrypt")
class AdminMediaDecryptController(
    private val adminMediaDecryptPort: AdminMediaDecryptPort,
    private val auditLogPort: AuditLogPort,
) {
    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping
    fun getConfig(): ResponseEntity<ApiResponse<MediaDecryptConfigResponse>> {
        return ResponseEntity.ok(ApiResponse.ok(currentConfig()))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/master")
    fun updateMaster(
        request: HttpServletRequest,
        @RequestBody body: MediaDecryptMasterRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val enabled = body.backendDecryptMasterEnabled
            ?: return badRequest("backendDecryptMasterEnabled 不能为空")
        val targetId = adminMediaDecryptPort.saveBooleanConfig(
            key = KEY_MEDIA_BACKEND_DECRYPT_MASTER_ENABLED,
            value = enabled,
            description = "媒体下载后端解密统一总开关",
        )

        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "UPDATE_MEDIA_DECRYPT_MASTER",
            targetType = "SYSTEM_CONFIG",
            targetId = targetId,
            details = "媒体下载后端解密统一开关${if (enabled) "开启" else "关闭"}",
            ipAddress = null,
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                currentConfig(backendDecryptMasterEnabled = enabled),
                "媒体下载解密统一开关已更新",
            ),
        )
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/default")
    fun updateDefault(
        request: HttpServletRequest,
        @RequestBody body: MediaDecryptDefaultRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val enabled = body.defaultBackendDecryptEnabled
            ?: return badRequest("defaultBackendDecryptEnabled 不能为空")
        val targetId = adminMediaDecryptPort.saveBooleanConfig(
            key = KEY_MEDIA_BACKEND_DECRYPT_ENABLED,
            value = enabled,
            description = "媒体下载兼容端口是否由后端解密",
        )

        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "UPDATE_MEDIA_DECRYPT_DEFAULT",
            targetType = "SYSTEM_CONFIG",
            targetId = targetId,
            details = "媒体下载后端解密默认${if (enabled) "开启" else "关闭"}",
            ipAddress = null,
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                currentConfig(defaultBackendDecryptEnabled = enabled),
                "媒体下载解密默认开关已更新",
            ),
        )
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PostMapping("/policies")
    fun createPolicy(
        request: HttpServletRequest,
        @RequestBody body: MediaDecryptPolicyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val policy = body.toPolicy() ?: return badRequest(body.validationError())
        val saved = adminMediaDecryptPort.savePolicy(policy)
        auditPolicyChange(request, "CREATE_MEDIA_DECRYPT_POLICY", saved)
        return ResponseEntity.ok(ApiResponse.ok(saved, "媒体下载解密策略已创建"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/policies/{id}")
    fun updatePolicy(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: MediaDecryptPolicyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val existing = adminMediaDecryptPort.findPolicy(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "策略不存在"))
        val next = body.toPolicy(existing) ?: return badRequest(body.validationError())
        val saved = adminMediaDecryptPort.savePolicy(next)
        auditPolicyChange(request, "UPDATE_MEDIA_DECRYPT_POLICY", saved)
        return ResponseEntity.ok(ApiResponse.ok(saved, "媒体下载解密策略已更新"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @DeleteMapping("/policies/{id}")
    fun deletePolicy(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        val existing = adminMediaDecryptPort.findPolicy(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "策略不存在"))
        adminMediaDecryptPort.deletePolicy(id)
        auditPolicyChange(request, "DELETE_MEDIA_DECRYPT_POLICY", existing)
        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "媒体下载解密策略已删除"))
    }

    private fun currentConfig(
        backendDecryptMasterEnabled: Boolean = adminMediaDecryptPort.globalMasterEnabled(),
        defaultBackendDecryptEnabled: Boolean = adminMediaDecryptPort.globalDefaultEnabled(),
    ): MediaDecryptConfigResponse {
        return MediaDecryptConfigResponse(
            backendDecryptMasterEnabled = backendDecryptMasterEnabled,
            defaultBackendDecryptEnabled = defaultBackendDecryptEnabled,
            policies = adminMediaDecryptPort.listPolicies(),
        )
    }

    private fun MediaDecryptPolicyRequest.toPolicy(existing: MediaDecryptPolicy? = null): MediaDecryptPolicy? {
        val parsedScope = parseScope(scope) ?: return null
        val normalizedUserId = StringValueRules.nonBlank(userId)
        val normalizedPlatform = StringValueRules.lowerNonBlank(platform)
        val normalizedMinVersion = StringValueRules.nonBlank(minClientVersion)
        val normalizedMaxVersion = StringValueRules.nonBlank(maxClientVersion)
        val normalizedNote = StringValueRules.nonBlank(note)

        if (backendDecryptEnabled == null) return null
        when (parsedScope) {
            MediaDecryptPolicyScope.USER -> if (normalizedUserId == null) return null
            MediaDecryptPolicyScope.PLATFORM_VERSION -> if (normalizedPlatform == null) return null
        }
        if (normalizedMinVersion != null && normalizedMaxVersion != null &&
            ClientVersionRules.compareVersions(normalizedMinVersion, normalizedMaxVersion) > 0
        ) {
            return null
        }

        val now = System.currentTimeMillis()
        return MediaDecryptPolicy(
            id = existing?.id,
            scope = parsedScope,
            enabled = enabled ?: existing?.enabled ?: true,
            backendDecryptEnabled = backendDecryptEnabled,
            userId = if (parsedScope == MediaDecryptPolicyScope.USER) normalizedUserId else null,
            platform = if (parsedScope == MediaDecryptPolicyScope.PLATFORM_VERSION) normalizedPlatform else null,
            minClientVersion = if (parsedScope == MediaDecryptPolicyScope.PLATFORM_VERSION) normalizedMinVersion else null,
            maxClientVersion = if (parsedScope == MediaDecryptPolicyScope.PLATFORM_VERSION) normalizedMaxVersion else null,
            priority = priority ?: existing?.priority ?: 0,
            note = normalizedNote,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun MediaDecryptPolicyRequest.validationError(): String {
        val parsedScope = parseScope(scope)
        return when {
            parsedScope == null -> "scope 必须是 USER 或 PLATFORM_VERSION"
            backendDecryptEnabled == null -> "backendDecryptEnabled 不能为空"
            parsedScope == MediaDecryptPolicyScope.USER && userId.isNullOrBlank() -> "用户策略必须填写 userId"
            parsedScope == MediaDecryptPolicyScope.PLATFORM_VERSION && platform.isNullOrBlank() -> "平台版本策略必须填写 platform"
            !minClientVersion.isNullOrBlank() && !maxClientVersion.isNullOrBlank() &&
                ClientVersionRules.compareVersions(
                    StringValueRules.nonBlankOr(minClientVersion, ""),
                    StringValueRules.nonBlankOr(maxClientVersion, ""),
                ) > 0 ->
                "minClientVersion 不能大于 maxClientVersion"
            else -> "策略参数不合法"
        }
    }

    private fun parseScope(scope: String?): MediaDecryptPolicyScope? =
        StringValueRules.nonBlank(scope)?.uppercase()?.let {
            runCatching { MediaDecryptPolicyScope.valueOf(it) }.getOrNull()
        }

    private fun auditPolicyChange(request: HttpServletRequest, action: String, policy: MediaDecryptPolicy) {
        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = action,
            targetType = "MEDIA_DECRYPT_POLICY",
            targetId = policy.id,
            details = policy.toAuditDetails(),
            ipAddress = null,
        )
    }

    private fun MediaDecryptPolicy.toAuditDetails(): String {
        val target = when (scope) {
            MediaDecryptPolicyScope.USER -> "用户 $userId"
            MediaDecryptPolicyScope.PLATFORM_VERSION -> listOfNotNull(
                platform,
                minClientVersion?.let { "min=$it" },
                maxClientVersion?.let { "max=$it" },
            ).joinToString(" ")
        }
        return "$target 后端解密${if (backendDecryptEnabled) "开启" else "关闭"} priority=$priority"
    }

    private fun adminId(request: HttpServletRequest): String = request.getAttribute("adminId") as String

    private fun adminUsername(request: HttpServletRequest): String =
        request.getAttribute("adminUsername") as? String ?: ""

    private fun badRequest(message: String): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, message))

    companion object {
        const val KEY_MEDIA_BACKEND_DECRYPT_MASTER_ENABLED = "media.proxy.backendDecrypt.masterEnabled"
        const val KEY_MEDIA_BACKEND_DECRYPT_ENABLED = "media.proxy.backendDecrypt.enabled"
    }
}
