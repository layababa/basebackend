package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.MessageDeliveryPolicyRequest
import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicy
import com.layababateam.xinxiwang_backend.service.AdminMessageDeliveryPolicyPort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
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
@RequestMapping("/api/admin/message-delivery-policies")
class AdminMessageDeliveryPolicyController(
    private val policyPort: AdminMessageDeliveryPolicyPort,
    private val auditLogPort: AuditLogPort,
) {
    @RequireAdmin("SUPER_ADMIN")
    @GetMapping
    fun listPolicies(): ResponseEntity<ApiResponse<List<MessageDeliveryPolicy>>> =
        ResponseEntity.ok(ApiResponse.ok(policyPort.listPolicies()))

    @RequireAdmin("SUPER_ADMIN")
    @PostMapping
    fun createPolicy(
        request: HttpServletRequest,
        @RequestBody body: MessageDeliveryPolicyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        body.validationError()?.let { validation ->
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, validation))
        }

        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        val policy = policyPort.savePolicy(body.toPolicy(createdBy = adminId, updatedBy = adminId))
        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "CREATE_MESSAGE_DELIVERY_POLICY",
            targetType = "MESSAGE_DELIVERY_POLICY",
            targetId = policy.id,
            details = "创建消息扩散策略: ${policy.name.ifBlank { policy.scope.name }} ${policy.mode}",
            ipAddress = null,
        )
        return ResponseEntity.ok(ApiResponse.ok(policy, "策略已创建"))
    }

    @RequireAdmin("SUPER_ADMIN")
    @PutMapping("/{id}")
    fun updatePolicy(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: MessageDeliveryPolicyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val existing = policyPort.findPolicy(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "策略不存在"))
        body.validationError()?.let { validation ->
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, validation))
        }

        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        val saved = policyPort.savePolicy(
            body.toPolicy(
                id = id,
                createdBy = existing.createdBy,
                createdAt = existing.createdAt,
                updatedBy = adminId,
            ),
        )
        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "UPDATE_MESSAGE_DELIVERY_POLICY",
            targetType = "MESSAGE_DELIVERY_POLICY",
            targetId = id,
            details = "更新消息扩散策略: ${saved.name.ifBlank { saved.scope.name }} ${saved.mode}",
            ipAddress = null,
        )
        return ResponseEntity.ok(ApiResponse.ok(saved, "策略已更新"))
    }

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/{id}")
    fun deletePolicy(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        if (!policyPort.policyExists(id)) {
            return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "策略不存在"))
        }

        val adminId = request.adminId()
        val adminUsername = request.adminUsername()
        policyPort.deletePolicy(id)
        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "DELETE_MESSAGE_DELIVERY_POLICY",
            targetType = "MESSAGE_DELIVERY_POLICY",
            targetId = id,
            details = "删除消息扩散策略",
            ipAddress = null,
        )
        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "策略已删除"))
    }

    private fun HttpServletRequest.adminId(): String = getAttribute("adminId") as String

    private fun HttpServletRequest.adminUsername(): String = getAttribute("adminUsername") as? String ?: ""
}
