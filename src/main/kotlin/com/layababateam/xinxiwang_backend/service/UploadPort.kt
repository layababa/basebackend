package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import org.springframework.web.multipart.MultipartFile

/** 文件上传能力端口。SDK 负责 HTTP 路由，接入方负责存储、媒体记录和缩略图实现。 */
interface UploadPort {
    fun uploadFile(
        file: MultipartFile,
        category: String,
        requestId: String?,
        userId: String?,
    ): UploadResponse

    fun presignEncrypted(body: Map<String, Any>): UploadResponse

    fun finalizeEncrypted(body: Map<String, Any>, userId: String?): UploadResponse

    fun presignDirectUpload(body: Map<String, Any>): UploadResponse
}

data class UploadResponse(
    val body: ApiResponse<*>,
    val status: Int = 200,
)
