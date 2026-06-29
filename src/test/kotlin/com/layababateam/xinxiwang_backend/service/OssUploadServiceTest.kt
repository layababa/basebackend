package com.layababateam.xinxiwang_backend.service

import com.aliyun.oss.OSS
import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import com.layababateam.xinxiwang_backend.repository.DebugLogReportRepository
import com.layababateam.xinxiwang_backend.repository.MediaObjectRepository
import java.lang.reflect.Proxy
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OssUploadServiceTest {
    @Test
    fun `direct presign matches rentmsg oss direct upload contract`() {
        val service = OssUploadService(
            ossService = OssService(
                ossInternal = ossProxy(),
                ossPublic = ossProxy(),
                ossUpload = ossProxy(),
                bucket = "rentmsg",
                debugLogPrefix = "debug-logs/",
            ),
            endpointResolver = MediaEndpointResolver(
                fallbackEndpoint = "https://oss.example.com/",
                directEndpoint = "",
            ),
            mediaKeyRegistry = MediaKeyRegistry("k1:not-used", "k1", "test"),
            mediaProxyTokenService = MediaProxyTokenService("secret"),
            mediaObjectRepository = unsupportedProxy(MediaObjectRepository::class.java),
            debugLogReportRepository = unsupportedProxy(DebugLogReportRepository::class.java),
            proxyPublicBase = "https://api.example.com/appserver",
            encryptedPlainReadyWaitMs = 0L,
        )

        val response = service.presignDirectUpload(
            mapOf(
                "extension" to "jpeg",
                "category" to "images",
                "contentType" to "application/octet-stream",
                "fileSize" to 123L,
                "hasThumb" to true,
            ),
        )

        assertEquals(200, response.status)
        val data = response.body.data as Map<*, *>
        assertEquals("oss-direct", data["uploadMode"])
        assertEquals(false, data["encrypted"])
        assertEquals("rentmsg", data["bucket"])
        assertEquals("https://oss.example.com", data["ossEndpoint"])
        assertTrue(data["ossKey"].toString().startsWith("images/"))
        assertTrue(data["ossKey"].toString().endsWith(".jpg"))
        assertTrue(data["url"].toString().startsWith("https://oss.example.com/images/"))
        assertTrue(data["putUrl"].toString().contains("signed=1"))
        assertTrue(data["thumbnailUrl"].toString().startsWith("https://oss.example.com/thumbnails/images/"))
        assertNotNull(data["thumbnailPutUrl"])
        assertNotNull(data["thumbnailOssKey"])
    }

    private fun ossProxy(): OSS {
        return Proxy.newProxyInstance(
            OSS::class.java.classLoader,
            arrayOf(OSS::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "generatePresignedUrl" -> URL("https://upload.example.com/object?signed=1")
                "doesObjectExist" -> false
                else -> throw UnsupportedOperationException(method.name)
            }
        } as OSS
    }

    private fun <T> unsupportedProxy(type: Class<T>): T {
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as T
    }
}
