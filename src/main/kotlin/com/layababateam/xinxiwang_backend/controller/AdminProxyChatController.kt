package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.ProxyChatPort
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ProxyChatStartRequest(
    @field:NotBlank(message = "被代聊用户 ID 不能为空")
    val userId: String,

    @field:Size(min = 1, max = 50, message = "目标用户数量必须在 1..50")
    val targetIds: List<String>,

    @field:NotBlank(message = "Bot 用户 ID 不能为空")
    val botUserId: String,

    val metadata: Map<String, String> = emptyMap(),
)

data class ProxyChatStopRequest(
    @field:NotBlank(message = "被代聊用户 ID 不能为空")
    val userId: String,

    @field:Size(min = 1, max = 50, message = "目标用户数量必须在 1..50")
    val targetIds: List<String>,
)

/** 管理端 AI 代聊配置接口；具体实现由业务仓的 ProxyChatPort Adapter 提供。 */
@RestController
@ConditionalOnBean(ProxyChatPort::class)
@RequestMapping("/api/admin/proxychat")
class AdminProxyChatController(
    private val proxyChatPort: ProxyChatPort,
) {
    @RequireAdmin
    @PostMapping("/start")
    fun start(@Valid @RequestBody body: ProxyChatStartRequest): ResponseEntity<ApiResponse<*>> = try {
        validateStart(body)
        proxyChatPort.startProxyChat(body.userId, body.targetIds, body.botUserId, body.metadata)
        ResponseEntity.ok(ApiResponse.ok(mapOf("code" to 200)))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
    } catch (e: IllegalStateException) {
        ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
    }

    @RequireAdmin
    @PostMapping("/stop")
    fun stop(@Valid @RequestBody body: ProxyChatStopRequest): ResponseEntity<ApiResponse<*>> = try {
        validateIds(body.userId, body.targetIds)
        proxyChatPort.stopProxyChat(body.userId, body.targetIds)
        ResponseEntity.ok(ApiResponse.ok(mapOf("code" to 200)))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
    }

    @RequireAdmin
    @GetMapping("/query")
    fun query(
        @RequestParam userId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<*>> = try {
        requireId(userId, "userId")
        require(page >= 1) { "page 必须从 1 开始" }
        require(size in 1..100) { "size 必须在 1..100" }
        ResponseEntity.ok(ApiResponse.ok(proxyChatPort.queryProxyChats(userId, page, size)))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
    }

    private fun validateStart(body: ProxyChatStartRequest) {
        validateIds(body.userId, body.targetIds)
        requireId(body.botUserId, "botUserId")
        require(body.metadata.size <= 32) { "metadata 最多 32 组" }
        body.metadata.forEach { (key, value) ->
            require(key.length <= 32) { "metadata key 长度不能超过 32" }
            require(value.length <= 512) { "metadata value 长度不能超过 512" }
        }
    }

    private fun validateIds(userId: String, targetIds: List<String>) {
        requireId(userId, "userId")
        require(targetIds.isNotEmpty() && targetIds.size <= 50) { "targetIds 数量必须在 1..50" }
        targetIds.forEach { requireId(it, "targetId") }
    }

    private fun requireId(value: String, name: String) {
        require(value.isNotBlank() && value.length <= 64) { "$name 长度必须在 1..64" }
    }
}
