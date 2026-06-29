package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("DEPRECATION")
class MultipartUploadControllerTest {
    @Test
    fun `retired multipart endpoints return gone with compatibility message`() {
        val controller = MultipartUploadController()

        val init = controller.initiate(
            MultipartUploadController.InitRequest(
                extension = "jpg",
                category = "images",
                contentType = "image/jpeg",
                fileSize = 1024,
            ),
        )
        val complete = controller.complete(
            MultipartUploadController.CompleteRequest(
                key = "images/a.jpg",
                uploadId = "upload-1",
                parts = listOf(MultipartUploadController.PartDto(partNumber = 1, etag = "etag")),
            ),
        )
        val abort = controller.abort(MultipartUploadController.AbortRequest(key = "images/a.jpg", uploadId = "upload-1"))

        listOf(init, complete, abort).forEach { response ->
            assertEquals(410, response.statusCode.value())
            val body = response.body as ApiResponse<*>
            assertEquals("Endpoint has been retired; use /api/upload or /api/upload/encrypted/*", body.message)
        }
    }
}
