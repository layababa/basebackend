package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.PaginationRules
import com.layababateam.xinxiwang_backend.service.StickerPort
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
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/stickers")
class StickerController(
    private val stickerPort: StickerPort,
) {
    @PostMapping("/upload")
    fun uploadSticker(
        request: HttpServletRequest,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        val sticker = stickerPort.uploadAndSaveSticker(userId, file)
        return ResponseEntity.ok(ApiResponse.ok(sticker))
    }

    @PostMapping
    fun favoriteSticker(
        request: HttpServletRequest,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        val url = body["url"]
        if (url.isNullOrEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error<Nothing>(ErrorCode.INVALID_PARAM, "URL 不能为空"))
        }

        val sticker = stickerPort.saveFavoriteSticker(userId, url)
        return ResponseEntity.ok(ApiResponse.ok(sticker))
    }

    @GetMapping
    fun getFavoriteStickers(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        val safeLimit = PaginationRules.pageSize(limit, MAX_LIMIT)
        val safePage = PaginationRules.zeroBasedPage(page)
        val allStickers = stickerPort.getFavoriteStickers(userId)
        val start = PaginationRules.offset(safePage, safeLimit)
        val paged = if (start < allStickers.size) {
            allStickers.subList(start, minOf(start + safeLimit, allStickers.size))
        } else {
            emptyList()
        }

        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "items" to paged,
                    "total" to allStickers.size,
                    "hasMore" to (start + safeLimit < allStickers.size),
                ),
            ),
        )
    }

    @DeleteMapping("/{id}")
    fun removeFavoriteSticker(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        val removed = stickerPort.removeFavoriteSticker(userId, id)

        if (removed) {
            return ResponseEntity.ok(ApiResponse.ok<Nothing>())
        }
        return ResponseEntity.status(404)
            .body(ApiResponse.error<Nothing>(ErrorCode.NOT_FOUND, "贴图不存在或无权限"))
    }

    private companion object {
        const val MAX_LIMIT = 100
    }
}
