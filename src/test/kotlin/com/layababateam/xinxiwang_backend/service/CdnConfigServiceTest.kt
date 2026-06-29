package com.layababateam.xinxiwang_backend.service

import com.aliyun.oss.OSS
import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.model.ServerNode
import com.layababateam.xinxiwang_backend.repository.ServerNodeRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class CdnConfigServiceTest {
    @Test
    fun `current cdn config uses node oss endpoints and grouped node lists`() {
        val nodes = listOf(
            ServerNode(
                id = "n2",
                name = "International",
                appServerUrl = "https://api-int.example.com/",
                websocketUrl = "wss://ws-int.example.com/",
                baseUrl = "https://int.example.com/",
                region = "international",
                sortOrder = 2,
            ),
            ServerNode(
                id = "n1",
                name = "China",
                appServerUrl = "https://api-cn.example.com/",
                websocketUrl = "wss://ws-cn.example.com/",
                baseUrl = "https://cn.example.com/",
                region = "china",
                sortOrder = 1,
                ossPublicEndpoint = "https://node-oss.example.com/",
                ossFailbackEndpoint = "https://node-oss-direct.example.com/",
            ),
        )
        val service = CdnConfigService(
            s3Client = null,
            ossService = OssService(ossProxy(), ossProxy(), ossProxy(), "rentmsg", "debug-logs/"),
            serverNodeRepository = serverNodeRepository(nodes),
            objectMapper = ObjectMapper(),
            bucketName = "media-bucket",
            publishTarget = "oss",
            ossEndpointPublic = "https://fallback-oss.example.com/",
            ossEndpointPublicDirect = "https://fallback-direct.example.com/",
        )

        val config = service.currentCdnConfig()

        assertEquals("https://node-oss.example.com/config/cdn.json", config["oss_url"])
        assertEquals("https://node-oss-direct.example.com/config/cdn.json", config["oss_failback_url"])
        val allNodes = config["nodes"] as List<*>
        assertEquals("n1", (allNodes[0] as Map<*, *>)["id"])
        assertEquals("https://api-cn.example.com/ping", (allNodes[0] as Map<*, *>)["speedTestUrl"])
        assertEquals(1, (config["china"] as List<*>).size)
        assertEquals(1, (config["international"] as List<*>).size)
    }

    private fun serverNodeRepository(nodes: List<ServerNode>): ServerNodeRepository {
        return Proxy.newProxyInstance(
            ServerNodeRepository::class.java.classLoader,
            arrayOf(ServerNodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "findByEnabledTrueOrderBySortOrderAsc" -> nodes
                else -> throw UnsupportedOperationException(method.name)
            }
        } as ServerNodeRepository
    }

    private fun ossProxy(): OSS {
        return Proxy.newProxyInstance(OSS::class.java.classLoader, arrayOf(OSS::class.java)) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as OSS
    }
}
