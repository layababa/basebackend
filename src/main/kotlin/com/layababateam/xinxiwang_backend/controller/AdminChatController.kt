package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AdminChatPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/chats")
class AdminChatController(
    private val adminChatPort: AdminChatPort,
) {

    @RequireAdmin("ADMIN")
    @GetMapping("/users/{userId}/conversations")
    fun getUserConversations(@PathVariable userId: String): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminChatPort.getUserConversations(userId)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/conversations/{convId}/messages")
    fun getConversationMessages(
        @PathVariable convId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminChatPort.getConversationMessages(convId, page, size)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/conversations/{convId}")
    fun getConversationDetail(@PathVariable convId: String): ResponseEntity<ApiResponse<Any>> {
        val conversation = adminChatPort.getConversationDetail(convId)
            ?: return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "会话不存在"))
        return ResponseEntity.ok(ApiResponse.ok(conversation))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/search")
    fun searchMessages(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) startDate: Long?,
        @RequestParam(required = false) endDate: Long?,
        @RequestParam(required = false) contentType: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<Any>> {
        val result = adminChatPort.searchMessages(keyword, userId, startDate, endDate, contentType, page, size)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @RequireAdmin("ADMIN")
    @DeleteMapping("/messages/{id}")
    fun deleteMessage(request: HttpServletRequest, @PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return handleMessageNotFound {
            val admin = request.adminContext()
            adminChatPort.deleteMessage(id, admin.id, admin.username, request.remoteAddr)
            ApiResponse.ok(message = "消息已删除")
        }
    }

    @RequireAdmin("ADMIN")
    @PutMapping("/messages/{id}/recall")
    fun recallMessage(request: HttpServletRequest, @PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return handleMessageNotFound {
            val admin = request.adminContext()
            adminChatPort.recallMessage(id, admin.id, admin.username, request.remoteAddr)
            ApiResponse.ok(message = "消息已撤回")
        }
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/broadcast")
    fun broadcast(
        request: HttpServletRequest,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<ApiResponse<Any>> {
        val content = body["content"] as? String
        val contentType = (body["contentType"] as? Number)?.toInt() ?: 0

        if (content.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.INVALID_PARAM, "消息内容不能为空"),
            )
        }

        return handleInvalidParam {
            val admin = request.adminContext()
            adminChatPort.broadcast(content, contentType, admin.id, admin.username, request.remoteAddr)
            ApiResponse.ok(message = "广播消息已提交，正在异步发送")
        }
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/multicast")
    fun multicast(
        request: HttpServletRequest,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<ApiResponse<Any>> {
        @Suppress("UNCHECKED_CAST")
        val userIds = (body["userIds"] as? List<String>) ?: emptyList()
        val content = body["content"] as? String
        val contentType = (body["contentType"] as? Number)?.toInt() ?: 0

        if (userIds.isEmpty()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.INVALID_PARAM, "用户列表不能为空"),
            )
        }
        if (content.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.INVALID_PARAM, "消息内容不能为空"),
            )
        }

        return handleInvalidParam {
            val admin = request.adminContext()
            adminChatPort.multicast(userIds, content, contentType, admin.id, admin.username, request.remoteAddr)
            ApiResponse.ok(data = mapOf("targetCount" to userIds.size), message = "组播消息已发送")
        }
    }

    private fun handleMessageNotFound(action: () -> ApiResponse<Any>): ResponseEntity<ApiResponse<Any>> {
        return try {
            ResponseEntity.ok(action())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).body(
                ApiResponse.error(ErrorCode.NOT_FOUND, e.message ?: "消息不存在"),
            )
        }
    }

    private fun handleInvalidParam(action: () -> ApiResponse<Any>): ResponseEntity<ApiResponse<Any>> {
        return try {
            ResponseEntity.ok(action())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.INVALID_PARAM, e.message ?: "参数错误"),
            )
        }
    }

    private fun HttpServletRequest.adminContext(): AdminContext {
        return AdminContext(
            id = getAttribute("adminId") as? String ?: "",
            username = getAttribute("adminUsername") as? String ?: "",
        )
    }

    private data class AdminContext(
        val id: String,
        val username: String,
    )
}
