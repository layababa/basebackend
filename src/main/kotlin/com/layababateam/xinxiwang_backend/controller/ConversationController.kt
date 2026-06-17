package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ConversationDto
import com.layababateam.xinxiwang_backend.dto.MessageDto
import com.layababateam.xinxiwang_backend.service.ConversationPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/conversation")
class ConversationController(
    private val conversationPort: ConversationPort,
) {
    @GetMapping("/list")
    fun getConversationList(request: HttpServletRequest): ResponseEntity<ApiResponse<List<ConversationDto>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(conversationPort.getConversationList(userId)))
    }

    @GetMapping("/{id}/history")
    fun getHistory(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam(required = false) beforeSeqId: Long?,
        @RequestParam(required = false, defaultValue = "30") limit: Int,
    ): ResponseEntity<ApiResponse<List<MessageDto>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(conversationPort.getHistory(userId, id, beforeSeqId, limit)))
    }

    @GetMapping("/{id}/media")
    fun getMediaMessages(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "1,3") types: String,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<List<MessageDto>>> {
        val userId = request.getAttribute("userId") as String
        val contentTypes = types.split(",").mapNotNull { it.trim().toIntOrNull() }
        return ResponseEntity.ok(ApiResponse.ok(conversationPort.getMediaMessages(userId, id, contentTypes, limit)))
    }

    @PostMapping("/{id}/read")
    fun updateReadPoint(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody(required = false) body: Map<String, Any?>?,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        val seqId = (body?.get("seqId") as? Number)?.toLong() ?: 0L
        conversationPort.updateReadPoint(userId, id, seqId)
        return ResponseEntity.ok(ApiResponse(true, "已读位点已更新"))
    }

    @GetMapping("/{id}/unread")
    fun getUnreadCount(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Map<String, Long>>> {
        val userId = request.getAttribute("userId") as String
        val count = conversationPort.getUnreadCount(userId, id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("unreadCount" to count)))
    }

    @DeleteMapping("/{id}")
    fun deleteConversation(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(deleteResult(conversationPort.deleteConversation(userId, id))))
    }

    @PostMapping("/{id}/clear-history")
    fun clearHistory(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(deleteResult(conversationPort.clearHistory(userId, id))))
    }

    private fun deleteResult(result: com.layababateam.xinxiwang_backend.service.ConversationDeleteResult): Map<String, Any> =
        mapOf(
            "isFriend" to result.isFriend,
            "hiddenBeforeSeqId" to result.hiddenBeforeSeqId,
            "deleted" to result.deleted,
        )
}
