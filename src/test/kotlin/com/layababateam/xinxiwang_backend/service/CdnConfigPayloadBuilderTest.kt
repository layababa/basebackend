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
                    ossPublicEndpoint = "https://ignored.example.com",
                ),
                node(
                    id = "a",
                    sortOrder = 1,
                    createdAt = 1,
                    ossPublicEndpoint = "https://oss.example.com/",
                    ossFailbackEndpoint = "https://oss-fallback.example.com/",
                ),
            ),
            versionMillis = 42,
        )

        assertEquals(42L, payload["version"])
        assertEquals("https://oss.example.com/config/cdn.json", payload["oss_url"])
        assertEquals("https://oss-fallback.example.com/config/cdn.json", payload["oss_failback_url"])
        val nodes = payload["nodes"] as List<*>
        val firstNode = nodes.first() as Map<*, *>
        assertEquals("a", firstNode["id"])
        assertFalse(firstNode.containsKey("ossPublicEndpoint"))
        assertFalse(firstNode.containsKey("ossFailbackEndpoint"))
    }

    @Test
    fun fallsBackToDefaultFailbackThenPrimaryRoot() {
        val defaultFallback = CdnConfigPayloadBuilder(
            defaultOssPublicEndpoint = "https://default.example.com/",
            defaultOssFailbackEndpoint = "https://default-fallback.example.com/",
        ).build(listOf(node(id = "a")), versionMillis = 1)

        assertEquals("https://default.example.com/config/cdn.json", defaultFallback["oss_url"])
        assertEquals("https://default-fallback.example.com/config/cdn.json", defaultFallback["oss_failback_url"])

        val primaryFallback = CdnConfigPayloadBuilder(
            defaultOssPublicEndpoint = "https://default.example.com/",
            defaultOssFailbackEndpoint = "",
        ).build(listOf(node(id = "a")), versionMillis = 1)

        assertEquals("https://default.example.com/config/cdn.json", primaryFallback["oss_url"])
        assertEquals("https://default.example.com/config/cdn.json", primaryFallback["oss_failback_url"])
    }

    private fun node(
        id: String,
        sortOrder: Int = 0,
        createdAt: Long = 0,
        ossPublicEndpoint: String? = null,
        ossFailbackEndpoint: String? = null,
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
    )
}
