package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.*
import com.layababateam.xinxiwang_backend.service.BusinessCardService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/cards")
class BusinessCardController(
    private val businessCardService: BusinessCardService
) {
    // ─── 我的名片 ──────────────────────────────────────────────────────────────

    @GetMapping("/my")
    fun getMyCards(
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<BusinessCardResponse>>> {
        val userId = request.getAttribute("userId") as String
        val cards = businessCardService.getCardsByUserId(userId).map { BusinessCardResponse.fromModel(it) }
        return ResponseEntity.ok(ApiResponse(true, "OK", cards))
    }

    @GetMapping("/my/default")
    fun getMyDefaultCard(
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<BusinessCardResponse?>> {
        val userId = request.getAttribute("userId") as String
        val card = businessCardService.getCardsByUserId(userId)
            .firstOrNull { it.isDefault }
            ?.let { BusinessCardResponse.fromModel(it) }
        return ResponseEntity.ok(ApiResponse(true, "OK", card))
    }

    @PostMapping
    fun createCard(
        request: HttpServletRequest,
        @Valid @RequestBody req: CreateBusinessCardRequest
    ): ResponseEntity<ApiResponse<BusinessCardResponse>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val card = businessCardService.createCard(userId, req)
            ResponseEntity.ok(ApiResponse(true, "OK", BusinessCardResponse.fromModel(card)))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "创建失败"))
        }
    }

    @PutMapping("/{cardId}")
    fun updateCard(
        request: HttpServletRequest,
        @PathVariable cardId: String,
        @Valid @RequestBody req: UpdateBusinessCardRequest
    ): ResponseEntity<ApiResponse<BusinessCardResponse>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val card = businessCardService.updateCard(userId, cardId, req)
            ResponseEntity.ok(ApiResponse(true, "OK", BusinessCardResponse.fromModel(card)))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "更新失败"))
        }
    }

    @DeleteMapping("/{cardId}")
    fun deleteCard(
        request: HttpServletRequest,
        @PathVariable cardId: String
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        return try {
            businessCardService.deleteCard(userId, cardId)
            ResponseEntity.ok(ApiResponse(true, "已删除"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "删除失败"))
        }
    }

    // ─── 查看他人名片（分享時使用）────────────────────────────────────────────

    @GetMapping("/{cardId}")
    fun getCard(
        request: HttpServletRequest,
        @PathVariable cardId: String
    ): ResponseEntity<ApiResponse<BusinessCardResponse>> {
        // userId already validated by interceptor
        return try {
            val card = businessCardService.getCardById(cardId)
            ResponseEntity.ok(ApiResponse(true, "OK", BusinessCardResponse.fromModel(card)))
        } catch (e: Exception) {
            ResponseEntity.status(404).body(ApiResponse(false, "名片不存在"))
        }
    }

    // ─── 取得某用戶的預設名片（按 userId 查詢，用於分享名片內容預覽）──────────

    @GetMapping("/user/{userId}/default")
    fun getUserDefaultCard(
        request: HttpServletRequest,
        @PathVariable userId: String
    ): ResponseEntity<ApiResponse<BusinessCardResponse?>> {
        // userId already validated by interceptor
        val card = businessCardService.getCardsByUserId(userId)
            .firstOrNull { it.isDefault }
            ?.let { BusinessCardResponse.fromModel(it) }
        return ResponseEntity.ok(ApiResponse(true, "OK", card))
    }

}
