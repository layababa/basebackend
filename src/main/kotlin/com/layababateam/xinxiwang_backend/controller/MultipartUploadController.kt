package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Deprecated(
    "Flutter clients no longer call this; kept for absolute backward compat. Returns 410 Gone.",
)
@RestController
@RequestMapping("/api/upload/multipart")
class MultipartUploadController {
    data class InitRequest(
        val extension: String,
        val category: String,
        val contentType: String,
        val fileSize: Long,
        val partSize: Long = 10L * 1024 * 1024,
    )

    data class InitResponse(
        val uploadId: String,
        val key: String,
        val presignedUrls: List<String>,
    )

    data class CompleteRequest(
        val key: String,
        val uploadId: String,
        val parts: List<PartDto>,
    )

    data class PartDto(
        val partNumber: Int,
        val etag: String,
    )

    data class AbortRequest(
        val key: String,
        val uploadId: String,
    )

    @PostMapping("/init")
    fun initiate(@RequestBody @Suppress("UNUSED_PARAMETER") request: InitRequest): ResponseEntity<ApiResponse<InitResponse>> =
        gone()

    @PostMapping("/complete")
    fun complete(@RequestBody @Suppress("UNUSED_PARAMETER") request: CompleteRequest): ResponseEntity<ApiResponse<Map<String, String>>> =
        gone()

    @PostMapping("/abort")
    fun abort(@RequestBody @Suppress("UNUSED_PARAMETER") request: AbortRequest): ResponseEntity<ApiResponse<Nothing>> =
        gone()

    private fun <T> gone(): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.status(410).body(
            ApiResponse.error<T>(
                ErrorCode.UNKNOWN_ERROR,
                "Endpoint has been retired; use /api/upload or /api/upload/encrypted/*",
            ),
        )
}
