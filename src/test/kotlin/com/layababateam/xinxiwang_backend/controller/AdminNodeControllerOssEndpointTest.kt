package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.CreateNodeRequest
import com.layababateam.xinxiwang_backend.dto.UpdateNodeRequest
import com.layababateam.xinxiwang_backend.model.ServerNode
import com.layababateam.xinxiwang_backend.service.AdminHttpAuditEvent
import com.layababateam.xinxiwang_backend.service.AdminNodePort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminNodeControllerOssEndpointTest {
    @Test
    fun createNodePersistsNormalizedOssEndpoints() {
        val port = FakeAdminNodePort()
        val controller = AdminNodeController(port, NoopAuditLogPort)

        val response = controller.createNode(
            request = adminRequest(),
            body = CreateNodeRequest(
                name = "Singapore",
                appServerUrl = "https://sg.example.com/appserver/",
                websocketUrl = "https://sg.example.com/websocket/",
                baseUrl = "https://sg.example.com/",
                region = "international",
                enabled = true,
                sortOrder = 7,
                ossPublicEndpoint = " https://oss.example.com/ ",
                ossFailbackEndpoint = "https://oss-fallback.example.com/",
            ),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals("https://oss.example.com", port.saved.single().ossPublicEndpoint)
        assertEquals(
            "https://oss-fallback.example.com",
            port.saved.single().ossFailbackEndpoint,
        )
    }

    @Test
    fun updateNodePreservesEndpointsWhenFieldsAreMissing() {
        val existing = serverNode(
            ossPublicEndpoint = "https://oss.example.com",
            ossFailbackEndpoint = "https://oss-fallback.example.com",
        )
        val port = FakeAdminNodePort(existing)
        val controller = AdminNodeController(port, NoopAuditLogPort)

        controller.updateNode(
            request = adminRequest(),
            id = existing.id!!,
            body = UpdateNodeRequest(name = "Updated"),
        )

        val saved = port.saved.single()
        assertEquals("https://oss.example.com", saved.ossPublicEndpoint)
        assertEquals("https://oss-fallback.example.com", saved.ossFailbackEndpoint)
    }

    @Test
    fun updateNodeClearsBlankOssEndpoints() {
        val existing = serverNode(
            ossPublicEndpoint = "https://oss.example.com",
            ossFailbackEndpoint = "https://oss-fallback.example.com",
        )
        val port = FakeAdminNodePort(existing)
        val controller = AdminNodeController(port, NoopAuditLogPort)

        controller.updateNode(
            request = adminRequest(),
            id = existing.id!!,
            body = UpdateNodeRequest(
                ossPublicEndpoint = "",
                ossFailbackEndpoint = "   ",
            ),
        )

        val saved = port.saved.single()
        assertNull(saved.ossPublicEndpoint)
        assertNull(saved.ossFailbackEndpoint)
    }
}

private fun serverNode(
    ossPublicEndpoint: String? = null,
    ossFailbackEndpoint: String? = null,
): ServerNode = ServerNode(
    id = "node-1",
    name = "Node",
    appServerUrl = "https://node.example.com/appserver",
    websocketUrl = "wss://node.example.com/websocket",
    baseUrl = "https://node.example.com",
    region = "international",
    ossPublicEndpoint = ossPublicEndpoint,
    ossFailbackEndpoint = ossFailbackEndpoint,
)

private class FakeAdminNodePort(initial: ServerNode? = null) : AdminNodePort {
    private val nodes = mutableMapOf<String, ServerNode>()
    val saved = mutableListOf<ServerNode>()
    var publishCount = 0

    init {
        if (initial != null) nodes[initial.id!!] = initial
    }

    override fun listNodes(): List<ServerNode> = nodes.values.toList()

    override fun findNode(id: String): ServerNode? = nodes[id]

    override fun saveNode(node: ServerNode): ServerNode {
        val savedNode = node.copy(id = node.id ?: "saved-${saved.size + 1}")
        nodes[savedNode.id!!] = savedNode
        saved += savedNode
        return savedNode
    }

    override fun deleteNode(id: String) {
        nodes.remove(id)
    }

    override fun publishCdnConfig() {
        publishCount += 1
    }
}

private object NoopAuditLogPort : AuditLogPort {
    override fun recordAudit(
        adminId: String,
        adminUsername: String,
        action: String,
        targetType: String,
        targetId: String?,
        details: String?,
        ipAddress: String?,
    ) = Unit

    override fun recordHttpAudit(event: AdminHttpAuditEvent) = Unit
}

private fun adminRequest(): HttpServletRequest =
    Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getAttribute" -> when (args?.firstOrNull()) {
                "adminId" -> "admin-1"
                "adminUsername" -> "root"
                else -> null
            }
            else -> when (method.returnType) {
                String::class.java -> ""
                Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
                Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
                Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
    } as HttpServletRequest
