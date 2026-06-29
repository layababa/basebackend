package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ContentType
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaContentUrlNormalizerTest {
    private val objectMapper: JsonMapper = JsonMapper.builder().build()

    @Test
    fun `rewrites old image payload to plaintext OSS and drops encrypted metadata`() {
        val normalized = MediaContentUrlNormalizer.normalize(
            """
            {
              "url":"https://s3.12da.rgzzsb.cn/encrypted/images/i1.bin",
              "ext":"png",
              "encrypted":{
                "mediaId":"i1",
                "cipherUrl":"https://s3.12da.rgzzsb.cn/encrypted/images/i1.bin",
                "cipherThumbUrl":"https://s3.12da.rgzzsb.cn/encrypted/thumbnails/images/i1.bin"
              },
              "proxyUrl":"https://12da.rgzzsb.cn/api/media/i1/token.png"
            }
            """.trimIndent(),
            ContentType.IMAGE.value,
            objectMapper,
        )
        val payload = payloadMap(normalized)

        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/images/i1.png", payload["url"])
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/thumbnails/images/i1.jpg", payload["thumbnailUrl"])
        assertFalse(payload.containsKey("encrypted"))
        assertFalse(payload.containsKey("proxyUrl"))
    }

    @Test
    fun `rewrites voice file and video urls using migration layouts`() {
        val voice = payloadMap(
            MediaContentUrlNormalizer.normalize(
                """{"url":"https://s3.12da.yufengep.com/encrypted/audio/a1.bin","duration":3}""",
                ContentType.VOICE.value,
                objectMapper,
            ),
        )
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/audio/a1.m4a", voice["url"])

        val file = payloadMap(
            MediaContentUrlNormalizer.normalize(
                """{"url":"https://s3.12da.rgzzsb.cn/encrypted/files/f1.bin","name":"contract.pdf","size":9}""",
                ContentType.FILE.value,
                objectMapper,
            ),
        )
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/files/f1.pdf", file["url"])

        val video = payloadMap(
            MediaContentUrlNormalizer.normalize(
                """{"url":"https://s3.12da.rgzzsb.cn/encrypted/videos/v1.bin","duration":5}""",
                ContentType.VIDEO.value,
                objectMapper,
                videoCompatPublicBase = "https://12da.yufengep.com/appserver",
            ),
        )
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/videos/v1.mp4", video["url"])
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/videos/v1.mp4", video["videoUrl"])
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/thumbnails/videos/v1.jpg", video["thumbnailUrl"])
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/thumbnails/videos/v1.jpg", video["previewUrl"])
    }

    @Test
    fun `preserves fallback encrypted media envelopes for local decrypt`() {
        val normalized = MediaContentUrlNormalizer.normalize(
            """
            {
              "url":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/images/i6.bin",
              "thumbnailUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/thumbnails/images/i6.bin",
              "encryptedFallback":true,
              "encrypted":{
                "mediaId":"i6",
                "cipherUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/images/i6.bin",
                "cipherThumbUrl":"https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/thumbnails/images/i6.bin"
              }
            }
            """.trimIndent(),
            ContentType.IMAGE.value,
            objectMapper,
            videoCompatPublicBase = "https://12da.yufengep.com/appserver",
        )
        val payload = payloadMap(normalized)
        val encrypted = payload["encrypted"] as Map<*, *>

        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/images/i6.bin", payload["url"])
        assertEquals("https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/thumbnails/images/i6.bin", payload["thumbnailUrl"])
        assertEquals(true, payload["encryptedFallback"])
        assertEquals("i6", encrypted["mediaId"])
    }

    @Test
    fun `leaves text content unchanged`() {
        val content = "https://s3.12da.rgzzsb.cn/encrypted/audio/a1.bin"

        assertEquals(
            content,
            MediaContentUrlNormalizer.normalize(content, ContentType.TEXT.value, objectMapper),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun payloadMap(payload: String): Map<String, Any?> =
        objectMapper.readValue(payload, Map::class.java) as Map<String, Any?>
}
