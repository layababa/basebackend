package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.BotApiAuthAttributes
import com.layababateam.xinxiwang_backend.service.ProxyChatPort
import jakarta.servlet.http.HttpServletRequest
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

data class BotProxyChatSendRequest(
    @field:NotBlank(message = "被代聊用户 ID 不能为空")
    val userId: String,

    @field:NotBlank(message = "沟通用户 ID 不能为空")
    val targetId: String,

    @field:NotBlank(message = "消息内容不能为空")
    @field:Size(max = 5000, message = "消息内容不超过 5000 字")
    val content: String,

    val contentType: Int = 0,
)

/** BotAI 代聊接口；由 Bot 鉴权拦截器识别 Authorization: Bot <api_key>。 */
@RestController
@ConditionalOnBean(ProxyChatPort::class)
@RequestMapping("/api/bot/proxychat")
class BotProxyChatController(
    private val proxyChatPort: ProxyChatPort,
) {
    @GetMapping("/list")
    fun list(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(proxyChatPort.listProxyChatsForBot(botUserId(request))))

    @GetMapping("/messages")
    fun messages(
        request: HttpServletRequest,
        @RequestParam userId: String,
        @RequestParam targetId: String,
        @RequestParam(required = false) afterSeqId: Long?,
        @RequestParam(required = false) beforeSeqId: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<*>> = try {
        validatePair(userId, targetId)
        require(limit in 1..500) { "limit 必须在 1..500" }
        ResponseEntity.ok(ApiResponse.ok(proxyChatPort.getProxyChatMessages(
            botUserId = botUserId(request),
            userId = userId,
            targetId = targetId,
            afterSeqId = afterSeqId,
            beforeSeqId = beforeSeqId,
            limit = limit,
        )))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
    } catch (e: IllegalStateException) {
        ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
    }

    @PostMapping("/send")
    fun send(
        request: HttpServletRequest,
        @Valid @RequestBody body: BotProxyChatSendRequest,
    ): ResponseEntity<ApiResponse<*>> = try {
        validatePair(body.userId, body.targetId)
        val msg = proxyChatPort.sendProxyChatMessage(
            botUserId = botUserId(request),
            userId = body.userId,
            targetId = body.targetId,
            content = body.content,
            contentType = body.contentType,
        )
        ResponseEntity.ok(ApiResponse.ok(mapOf(
            "messageId" to msg.messageId,
            "conversationId" to msg.conversationId,
            "seqId" to msg.seqId,
        )))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
    } catch (e: IllegalStateException) {
        ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
    }

    private fun botUserId(request: HttpServletRequest): String =
        request.getAttribute(BotApiAuthAttributes.BOT_USER_ID_ATTR) as String

    private fun validatePair(userId: String, targetId: String) {
        requireId(userId, "userId")
        requireId(targetId, "targetId")
    }

    private fun requireId(value: String, name: String) {
        require(value.isNotBlank() && value.length <= 64) { "$name 长度必须在 1..64" }
    }
}
