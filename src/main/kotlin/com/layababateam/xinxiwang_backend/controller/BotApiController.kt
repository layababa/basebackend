package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.BotApiAuthAttributes
import com.layababateam.xinxiwang_backend.service.BotApiPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class BotSendMessageRequest(
    @field:NotBlank(message = "会话ID不能为空")
    val conversationId: String,

    @field:NotBlank(message = "消息内容不能为空")
    @field:Size(max = 5000, message = "消息内容不超过 5000 字")
    val content: String,

    val contentType: Int = 0,
)

@RestController
@RequestMapping("/api/bot")
class BotApiController(
    private val botApiPort: BotApiPort,
) {

    @GetMapping("/me")
    fun getMe(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute(BotApiAuthAttributes.BOT_USER_ID_ATTR) as String
        val profile = botApiPort.getBotProfile(userId)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "Bot 用户不存在"))
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "id" to profile.id,
                    "username" to profile.username,
                    "displayName" to profile.displayName,
                    "avatarUrl" to profile.avatarUrl,
                    "description" to profile.description,
                    "status" to profile.status,
                ),
            ),
        )
    }

    @PostMapping("/sendMessage")
    fun sendMessage(
        request: HttpServletRequest,
        @Valid @RequestBody body: BotSendMessageRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute(BotApiAuthAttributes.BOT_USER_ID_ATTR) as String
        return try {
            val msg = botApiPort.sendBotMessage(
                userId = userId,
                conversationId = body.conversationId,
                content = body.content,
                contentType = body.contentType,
            )
            ResponseEntity.ok(
                ApiResponse.ok(
                    mapOf(
                        "messageId" to msg.messageId,
                        "conversationId" to msg.conversationId,
                        "seqId" to msg.seqId,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/conversations")
    fun getConversations(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute(BotApiAuthAttributes.BOT_USER_ID_ATTR) as String
        return ResponseEntity.ok(ApiResponse.ok(botApiPort.getBotConversations(userId)))
    }
}
