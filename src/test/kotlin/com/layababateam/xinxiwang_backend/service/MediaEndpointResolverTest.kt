package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ServerNode
import com.layababateam.xinxiwang_backend.repository.ServerNodeRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaEndpointResolverTest {
    @Test
    fun `uses first enabled node oss endpoints before configured fallback`() {
        val repository = repository(
            listOf(
                node(
                    id = "later",
                    sortOrder = 10,
                    createdAt = 1,
                    ossPublicEndpoint = "https://later.example.com/",
                    ossFailbackEndpoint = "https://later-fallback.example.com/",
                ),
                node(
                    id = "first",
                    sortOrder = 1,
                    createdAt = 2,
                    ossPublicEndpoint = " https://primary.example.com/// ",
                    ossFailbackEndpoint = " https://failback.example.com/// ",
                ),
            )
        )

        val resolver = MediaEndpointResolver(
            fallbackEndpoint = "https://configured.example.com",
            directEndpoint = "https://configured-direct.example.com",
            serverNodeRepository = repository,
        )

        assertEquals("https://primary.example.com", resolver.currentOssPublicEndpoint())
        assertEquals("https://failback.example.com", resolver.currentOssFailbackEndpoint())
    }

    @Test
    fun `refresh reloads node oss endpoint changes`() {
        var nodes = listOf(
            node(
                id = "first",
                ossPublicEndpoint = "https://old.example.com",
                ossFailbackEndpoint = "https://old-failback.example.com",
            )
        )
        val repository = repository { nodes }
        val resolver = MediaEndpointResolver(
            fallbackEndpoint = "https://configured.example.com",
            directEndpoint = "https://configured-direct.example.com",
            serverNodeRepository = repository,
        )

        nodes = listOf(
            node(
                id = "first",
                ossPublicEndpoint = "https://new.example.com",
                ossFailbackEndpoint = "https://new-failback.example.com",
            )
        )
        resolver.refresh()

        assertEquals("https://new.example.com", resolver.currentOssPublicEndpoint())
        assertEquals("https://new-failback.example.com", resolver.currentOssFailbackEndpoint())
    }

    @Test
    fun `falls back to configured direct endpoint when no node endpoint exists`() {
        val resolver = MediaEndpointResolver(
            fallbackEndpoint = "https://xianyunimint.oss-accelerate.aliyuncs.com/",
            directEndpoint = "https://xianyunimint.oss-cn-hongkong.aliyuncs.com/",
            serverNodeRepository = repository(
                listOf(node(id = "empty", ossPublicEndpoint = " ", ossFailbackEndpoint = ""))
            ),
        )

        assertEquals(
            "https://xianyunimint.oss-cn-hongkong.aliyuncs.com",
            resolver.currentOssPublicEndpoint(),
        )
        assertEquals(
            "https://xianyunimint.oss-cn-hongkong.aliyuncs.com",
            resolver.currentOssFailbackEndpoint(),
        )
    }

    private fun repository(nodes: List<ServerNode>): ServerNodeRepository =
        repository { nodes }

    private fun repository(nodesProvider: () -> List<ServerNode>): ServerNodeRepository =
        Proxy.newProxyInstance(
            ServerNodeRepository::class.java.classLoader,
            arrayOf(ServerNodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "findByEnabledTrueOrderBySortOrderAsc" -> nodesProvider()
                    .filter { it.enabled }
                    .sortedWith(compareBy<ServerNode> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
                "toString" -> "FakeServerNodeRepository"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as ServerNodeRepository

    private fun node(
        id: String,
        enabled: Boolean = true,
        sortOrder: Int = 0,
        createdAt: Long = 1,
        ossPublicEndpoint: String? = null,
        ossFailbackEndpoint: String? = null,
    ) = ServerNode(
        id = id,
        name = "Node $id",
        appServerUrl = "https://api-$id.example.com",
        websocketUrl = "wss://ws-$id.example.com",
        baseUrl = "https://base-$id.example.com",
        region = "china",
        enabled = enabled,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = createdAt,
        ossPublicEndpoint = ossPublicEndpoint,
        ossFailbackEndpoint = ossFailbackEndpoint,
    )
}
