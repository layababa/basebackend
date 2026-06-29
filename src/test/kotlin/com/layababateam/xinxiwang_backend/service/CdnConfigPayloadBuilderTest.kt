package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ServerNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CdnConfigPayloadBuilderTest {
    @Test
    fun buildsTopLevelOssUrlsFromFirstEnabledNodeWithoutLeakingNodeFields() {
        val payload = CdnConfigPayloadBuilder(
            defaultOssPublicEndpoint = "https://default.example.com",
            defaultOssFailbackEndpoint = "https://default-fallback.example.com",
        ).build(
            nodes = listOf(
                node(
                    id = "b",
                    sortOrder = 2,
                    createdAt = 2,
                    ossPublicEndpoint = "https://ignored-bucket.oss-cn-hongkong.aliyuncs.com",
                ),
                node(
                    id = "a",
                    sortOrder = 1,
                    createdAt = 1,
                    ossPublicEndpoint = "https://primary-bucket.oss-cn-hongkong.aliyuncs.com/",
                    ossFailbackEndpoint = "https://fallback-bucket.oss-cn-hongkong.aliyuncs.com/",
                    ossAccessKeyId = "ENC:A256GCM:v1:k1:id",
                    ossAccessKeySecret = "ENC:A256GCM:v1:k1:secret",
                    ossFailbackAccessKeyId = "ENC:A256GCM:v1:k1:fb-id",
                    ossFailbackAccessKeySecret = "ENC:A256GCM:v1:k1:fb-secret",
                ),
            ),
            versionMillis = 42,
        )

        assertEquals(42L, payload["version"])
        assertEquals("https://primary-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json", payload["oss_url"])
        assertEquals("ENC:A256GCM:v1:k1:id", payload["oss_access_key_id"])
        assertEquals("ENC:A256GCM:v1:k1:secret", payload["oss_access_key_secret"])
        assertEquals("https://fallback-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json", payload["oss_failback_url"])
        assertEquals("ENC:A256GCM:v1:k1:fb-id", payload["oss_failback_access_key_id"])
        assertEquals("ENC:A256GCM:v1:k1:fb-secret", payload["oss_failback_access_key_secret"])
        val nodes = payload["nodes"] as List<*>
        val firstNode = nodes.first() as Map<*, *>
        assertEquals("a", firstNode["id"])
        assertFalse(firstNode.containsKey("ossPublicEndpoint"))
        assertFalse(firstNode.containsKey("ossFailbackEndpoint"))
        assertFalse(firstNode.containsKey("ossAccessKeyId"))
        assertFalse(firstNode.containsKey("ossAccessKeySecret"))
        assertFalse(firstNode.containsKey("ossFailbackAccessKeyId"))
        assertFalse(firstNode.containsKey("ossFailbackAccessKeySecret"))
    }

    @Test
    fun fallsBackToDefaultFailbackThenPrimaryRoot() {
        val defaultFallback = CdnConfigPayloadBuilder(
            defaultOssPublicEndpoint = "https://default-bucket.oss-cn-hongkong.aliyuncs.com/",
            defaultOssFailbackEndpoint = "https://default-fallback-bucket.oss-cn-hongkong.aliyuncs.com/",
        ).build(listOf(node(id = "a")), versionMillis = 1)

        assertEquals("https://default-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json", defaultFallback["oss_url"])
        assertEquals("https://default-fallback-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json", defaultFallback["oss_failback_url"])

        val primaryFallback = CdnConfigPayloadBuilder(
            defaultOssPublicEndpoint = "https://default-bucket.oss-cn-hongkong.aliyuncs.com/",
            defaultOssFailbackEndpoint = "",
        ).build(listOf(node(id = "a")), versionMillis = 1)

        assertEquals("https://default-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json", primaryFallback["oss_url"])
        assertEquals("https://default-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json", primaryFallback["oss_failback_url"])
    }

    private fun node(
        id: String,
        sortOrder: Int = 0,
        createdAt: Long = 0,
        ossPublicEndpoint: String? = null,
        ossFailbackEndpoint: String? = null,
        ossAccessKeyId: String? = null,
        ossAccessKeySecret: String? = null,
        ossFailbackAccessKeyId: String? = null,
        ossFailbackAccessKeySecret: String? = null,
    ) = ServerNode(
        id = id,
        name = "Node $id",
        appServerUrl = "https://api-$id.example.com",
        websocketUrl = "wss://ws-$id.example.com",
        baseUrl = "https://web-$id.example.com",
        region = "china",
        enabled = true,
        sortOrder = sortOrder,
        createdAt = createdAt,
        ossPublicEndpoint = ossPublicEndpoint,
        ossFailbackEndpoint = ossFailbackEndpoint,
        ossAccessKeyId = ossAccessKeyId,
        ossAccessKeySecret = ossAccessKeySecret,
        ossFailbackAccessKeyId = ossFailbackAccessKeyId,
        ossFailbackAccessKeySecret = ossFailbackAccessKeySecret,
    )
}
