package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.UploadPort
import com.layababateam.xinxiwang_backend.service.UploadResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 通用上传入口。
 *
 * SDK 保留客户端协议和路由，具体 OSS/媒体记录/缩略图处理由业务侧 UploadPort 实现。
 */
@RestController
@RequestMapping("/api/upload")
class UploadController(
    private val uploadPort: UploadPort,
) {
    @PostMapping
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("category", defaultValue = "images") category: String,
        @RequestParam("requestId", required = false) requestId: String?,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return uploadPort.uploadFile(file, category, requestId, userId(request)).toResponse()
    }

    @PostMapping("/encrypted/presign")
    fun presignEncrypted(@RequestBody body: Map<String, Any>): ResponseEntity<ApiResponse<*>> {
        return uploadPort.presignEncrypted(body).toResponse()
    }

    @PostMapping("/encrypted/finalize")
    fun finalizeEncrypted(
        @RequestBody body: Map<String, Any>,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return uploadPort.finalizeEncrypted(body, userId(request)).toResponse()
    }

    @PostMapping("/direct/presign")
    fun presignDirectUpload(@RequestBody body: Map<String, Any>): ResponseEntity<ApiResponse<*>> {
        return uploadPort.presignDirectUpload(body).toResponse()
    }

    private fun userId(request: HttpServletRequest): String? =
        request.getAttribute("userId") as? String

    private fun UploadResponse.toResponse(): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.status(status).body(body)
}
