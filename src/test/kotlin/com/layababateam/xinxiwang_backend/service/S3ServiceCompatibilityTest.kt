package com.layababateam.xinxiwang_backend.service

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Suppress("DEPRECATION")
class S3ServiceCompatibilityTest {
    @Test
    fun `legacy thumbnail urls follow bucket domain layout`() {
        val service = S3Service(
            s3Client = unsupportedProxy(S3Client::class.java),
            s3Presigner = unsupportedProxy(S3Presigner::class.java),
            s3DownloadPresigner = unsupportedProxy(S3Presigner::class.java),
            bucketName = "media-bucket",
            bucketDomain = "https://media.example.com",
            debugLogPrefix = "debug-logs/",
        )

        assertEquals(
            "https://media.example.com/thumbnails/images/m1.jpg",
            service.generateThumbnailUrl("https://media.example.com/images/m1.jpg"),
        )
        assertEquals(
            "https://other.example.com/images/m1.jpg",
            service.generateThumbnailUrl("https://other.example.com/images/m1.jpg"),
        )
    }

    private fun <T> unsupportedProxy(type: Class<T>): T {
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as T
    }
}
