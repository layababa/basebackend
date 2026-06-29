package com.layababateam.xinxiwang_backend.service

import com.aliyun.oss.OSS
import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import com.layababateam.xinxiwang_backend.model.ContentType
import com.layababateam.xinxiwang_backend.repository.MediaObjectRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoricalEncryptedMediaFallbackServiceTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `repairs legacy image payload from source encrypted metadata when plaintext objects are missing`() {
        val mediaId = "42790a5452a94ce8a3ebe963ecc68b77"
        val service = HistoricalEncryptedMediaFallbackService(
            ossService = OssService(
                ossInternal = ossProxy(
                    existingKeys = setOf(
                        "encrypted/images/$mediaId.bin",
                        "encrypted/thumbnails/images/$mediaId.bin",
                    ),
                ),
                ossPublic = ossProxy(),
                ossUpload = ossProxy(),
                bucket = "rentmsg",
                debugLogPrefix = "debug-logs/",
            ),
            mediaKeyRegistry = MediaKeyRegistry("k1:not-used", "k1", "test"),
            endpointResolver = MediaEndpointResolver(
                fallbackEndpoint = "https://rentmsg-hk.oss-accelerate.aliyuncs.com",
                directEndpoint = "",
                serverNodeRepository = emptyServerNodeRepository(),
            ),
            mediaObjectRepository = mediaObjectRepository(),
            objectMapper = objectMapper,
        )
        val content = """
            {
              "url":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/images/$mediaId.png",
              "thumbnailUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/thumbnails/images/$mediaId.jpg"
            }
        """.trimIndent()
        val sourceContent = """
            {
              "url":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/images/$mediaId.png",
              "thumbnailUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/thumbnails/images/$mediaId.jpg",
              "encrypted":{
                "mediaId":"$mediaId",
                "ossKey":"encrypted/images/$mediaId.bin",
                "thumbOssKey":"encrypted/thumbnails/images/$mediaId.bin",
                "cipherUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/images/$mediaId.bin",
                "cipherThumbUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/thumbnails/images/$mediaId.bin",
                "keyId":"legacy-key",
                "alg":"AES-256-GCM"
              }
            }
        """.trimIndent()

        val repaired = service.apply(content, ContentType.IMAGE.value, sourceContent)
        val payload = objectMapper.readValue(repaired, Map::class.java)
        val encrypted = payload["encrypted"] as Map<*, *>

        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/images/$mediaId.bin",
            payload["url"],
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/thumbnails/images/$mediaId.bin",
            payload["thumbnailUrl"],
        )
        assertEquals(true, payload["encryptedFallback"])
        assertEquals("oss-encrypted-direct-fallback", payload["uploadMode"])
        assertEquals("legacy-key", encrypted["keyId"])
        assertEquals("encrypted/images/$mediaId.bin", encrypted["ossKey"])
    }

    private fun ossProxy(existingKeys: Set<String> = emptySet()): OSS {
        return Proxy.newProxyInstance(
            OSS::class.java.classLoader,
            arrayOf(OSS::class.java),
        ) { _, method, args ->
            when (method.name) {
                "doesObjectExist" -> args?.getOrNull(1) in existingKeys
                else -> throw UnsupportedOperationException(method.name)
            }
        } as OSS
    }

    private fun mediaObjectRepository(): MediaObjectRepository {
        return Proxy.newProxyInstance(
            MediaObjectRepository::class.java.classLoader,
            arrayOf(MediaObjectRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "findFirstByMediaId" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        } as MediaObjectRepository
    }
}
