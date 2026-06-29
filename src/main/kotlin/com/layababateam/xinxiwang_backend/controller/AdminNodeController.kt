package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CreateNodeRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.UpdateNodeRequest
import com.layababateam.xinxiwang_backend.model.ServerNode
import com.layababateam.xinxiwang_backend.service.AdminNodeOssEndpointRules
import com.layababateam.xinxiwang_backend.service.AdminNodePort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import com.layababateam.xinxiwang_backend.service.UrlRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
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
@RequestMapping("/api/admin/system/nodes")
class AdminNodeController(
    private val adminNodePort: AdminNodePort,
    private val auditLogPort: AuditLogPort,
) {
    private val log = LoggerFactory.getLogger(AdminNodeController::class.java)

    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping
    fun listNodes(): ResponseEntity<ApiResponse<List<ServerNode>>> {
        return ResponseEntity.ok(ApiResponse.ok(adminNodePort.listNodes()))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PostMapping
    fun createNode(
        request: HttpServletRequest,
        @Valid @RequestBody body: CreateNodeRequest,
    ): ResponseEntity<ApiResponse<ServerNode>> {
        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        val ossFields = normalizedCreateOssFields(body)

        val node = adminNodePort.saveNode(
            ServerNode(
                name = body.name,
                appServerUrl = UrlRules.stripTrailingSlash(body.appServerUrl),
                websocketUrl = normalizeWsUrl(body.websocketUrl),
                baseUrl = UrlRules.stripTrailingSlash(body.baseUrl),
                region = body.region,
                enabled = body.enabled,
                sortOrder = body.sortOrder,
                ossPublicEndpoint = ossFields.publicEndpoint,
                ossFailbackEndpoint = ossFields.failbackEndpoint,
                ossAccessKeyId = ossFields.accessKeyId,
                ossAccessKeySecret = ossFields.accessKeySecret,
                ossFailbackAccessKeyId = ossFields.failbackAccessKeyId,
                ossFailbackAccessKeySecret = ossFields.failbackAccessKeySecret,
            ),
        )

        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "CREATE_SERVER_NODE",
            targetType = "SERVER_NODE",
            targetId = node.id,
            details = "创建节点: ${node.name} (${node.region})",
            ipAddress = null,
        )

        publishCdnConfigSafely()
        return ResponseEntity.ok(ApiResponse.ok(node, "节点创建成功"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/{id}")
    fun updateNode(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateNodeRequest,
    ): ResponseEntity<ApiResponse<ServerNode>> {
        val existing = adminNodePort.findNode(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "节点不存在"))

        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        val ossFields = normalizedUpdateOssFields(body, existing)

        val updated = existing.copy(
            name = body.name ?: existing.name,
            appServerUrl = UrlRules.stripTrailingSlashOrNull(body.appServerUrl) ?: existing.appServerUrl,
            websocketUrl = body.websocketUrl?.let { normalizeWsUrl(it) } ?: existing.websocketUrl,
            baseUrl = UrlRules.stripTrailingSlashOrNull(body.baseUrl) ?: existing.baseUrl,
            region = body.region ?: existing.region,
            enabled = body.enabled ?: existing.enabled,
            sortOrder = body.sortOrder ?: existing.sortOrder,
            ossPublicEndpoint = ossFields.publicEndpoint,
            ossFailbackEndpoint = ossFields.failbackEndpoint,
            ossAccessKeyId = ossFields.accessKeyId,
            ossAccessKeySecret = ossFields.accessKeySecret,
            ossFailbackAccessKeyId = ossFields.failbackAccessKeyId,
            ossFailbackAccessKeySecret = ossFields.failbackAccessKeySecret,
            updatedAt = System.currentTimeMillis(),
        )
        val saved = adminNodePort.saveNode(updated)

        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "UPDATE_SERVER_NODE",
            targetType = "SERVER_NODE",
            targetId = id,
            details = "更新节点: ${saved.name} (${saved.region})",
            ipAddress = null,
        )

        publishCdnConfigSafely()
        return ResponseEntity.ok(ApiResponse.ok(saved, "节点更新成功"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @DeleteMapping("/{id}")
    fun deleteNode(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        val existing = adminNodePort.findNode(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "节点不存在"))

        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        adminNodePort.deleteNode(id)

        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "DELETE_SERVER_NODE",
            targetType = "SERVER_NODE",
            targetId = id,
            details = "删除节点: ${existing.name} (${existing.region})",
            ipAddress = null,
        )

        publishCdnConfigSafely()
        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "节点已删除"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PostMapping("/publish")
    fun publishCdnConfig(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        adminNodePort.publishCdnConfig()

        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "PUBLISH_CDN_CONFIG",
            targetType = "CDN_CONFIG",
            targetId = null,
            details = "手动发布 CDN 配置",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "CDN 配置已发布"))
    }

    private fun normalizedCreateOssFields(body: CreateNodeRequest): OssFields {
        val fields = OssFields(
            publicEndpoint = normalizeOssRoot(body.ossPublicEndpoint, "Primary OSS endpoint"),
            failbackEndpoint = normalizeOssRoot(body.ossFailbackEndpoint, "Failback OSS endpoint"),
            accessKeyId = normalizeCredential(body.ossAccessKeyId),
            accessKeySecret = normalizeCredential(body.ossAccessKeySecret),
            failbackAccessKeyId = normalizeCredential(body.ossFailbackAccessKeyId),
            failbackAccessKeySecret = normalizeCredential(body.ossFailbackAccessKeySecret),
        )
        validateOssFields(fields)
        return fields
    }

    private fun normalizedUpdateOssFields(body: UpdateNodeRequest, existing: ServerNode): OssFields {
        val fields = OssFields(
            publicEndpoint = mergeOptionalOssRoot(body.ossPublicEndpoint, existing.ossPublicEndpoint, "Primary OSS endpoint"),
            failbackEndpoint = mergeOptionalOssRoot(body.ossFailbackEndpoint, existing.ossFailbackEndpoint, "Failback OSS endpoint"),
            accessKeyId = mergeOptionalCredential(body.ossAccessKeyId, existing.ossAccessKeyId),
            accessKeySecret = mergeOptionalCredential(body.ossAccessKeySecret, existing.ossAccessKeySecret),
            failbackAccessKeyId = mergeOptionalCredential(body.ossFailbackAccessKeyId, existing.ossFailbackAccessKeyId),
            failbackAccessKeySecret = mergeOptionalCredential(
                body.ossFailbackAccessKeySecret,
                existing.ossFailbackAccessKeySecret,
            ),
        )
        validateOssFields(fields)
        return fields
    }

    private fun validateOssFields(fields: OssFields) {
        AdminNodeOssEndpointRules.validateEndpointCredentialPair(
            fields.publicEndpoint,
            fields.accessKeyId,
            fields.accessKeySecret,
            "Primary OSS",
        )
        AdminNodeOssEndpointRules.validateEndpointCredentialPair(
            fields.failbackEndpoint,
            fields.failbackAccessKeyId,
            fields.failbackAccessKeySecret,
            "Failback OSS",
        )
    }

    private fun normalizeWsUrl(url: String): String {
        val trimmed = UrlRules.stripTrailingSlash(url)
        return when {
            trimmed.startsWith("https://") -> trimmed.replaceFirst("https://", "wss://")
            trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "ws://")
            else -> trimmed
        }
    }

    private fun normalizeOssRoot(value: String?, label: String): String? =
        AdminNodeOssEndpointRules.normalizeOptionalRootUrl(value, label)

    private fun normalizeCredential(value: String?): String? =
        AdminNodeOssEndpointRules.normalizeOptionalCredential(value)

    private fun mergeOptionalOssRoot(value: String?, existing: String?, label: String): String? {
        return if (value.isNullOrBlank()) existing else normalizeOssRoot(value, label)
    }

    private fun mergeOptionalCredential(value: String?, existing: String?): String? {
        return if (value.isNullOrBlank()) existing else normalizeCredential(value)
    }

    private fun publishCdnConfigSafely() {
        try {
            adminNodePort.publishCdnConfig()
        } catch (e: Exception) {
            log.error("自动发布 cdn.json 失败", e)
        }
    }

    private data class OssFields(
        val publicEndpoint: String?,
        val failbackEndpoint: String?,
        val accessKeyId: String?,
        val accessKeySecret: String?,
        val failbackAccessKeyId: String?,
        val failbackAccessKeySecret: String?,
    )

    private fun HttpServletRequest.adminId(): String = getAttribute("adminId") as String

    private fun HttpServletRequest.adminUsername(): String = getAttribute("adminUsername") as? String ?: ""
}
