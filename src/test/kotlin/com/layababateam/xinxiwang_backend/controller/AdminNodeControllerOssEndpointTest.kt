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
import kotlin.test.assertFailsWith
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
                ossPublicEndpoint = " https://primary-bucket.oss-cn-hongkong.aliyuncs.com/ ",
                ossFailbackEndpoint = "https://fallback-bucket.oss-cn-hongkong.aliyuncs.com/",
                ossAccessKeyId = " ENC:A256GCM:v1:k1:id ",
                ossAccessKeySecret = " ENC:A256GCM:v1:k1:secret ",
                ossFailbackAccessKeyId = "ENC:A256GCM:v1:k1:fb-id",
                ossFailbackAccessKeySecret = "ENC:A256GCM:v1:k1:fb-secret",
            ),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals("https://primary-bucket.oss-cn-hongkong.aliyuncs.com", port.saved.single().ossPublicEndpoint)
        assertEquals(
            "https://fallback-bucket.oss-cn-hongkong.aliyuncs.com",
            port.saved.single().ossFailbackEndpoint,
        )
        assertEquals("ENC:A256GCM:v1:k1:id", port.saved.single().ossAccessKeyId)
        assertEquals("ENC:A256GCM:v1:k1:secret", port.saved.single().ossAccessKeySecret)
        assertEquals("ENC:A256GCM:v1:k1:fb-id", port.saved.single().ossFailbackAccessKeyId)
        assertEquals("ENC:A256GCM:v1:k1:fb-secret", port.saved.single().ossFailbackAccessKeySecret)
    }

    @Test
    fun updateNodePreservesEndpointsWhenFieldsAreMissing() {
        val existing = serverNode(
            ossPublicEndpoint = "https://primary-bucket.oss-cn-hongkong.aliyuncs.com",
            ossFailbackEndpoint = "https://fallback-bucket.oss-cn-hongkong.aliyuncs.com",
            ossAccessKeyId = "ENC:A256GCM:v1:k1:id",
            ossAccessKeySecret = "ENC:A256GCM:v1:k1:secret",
            ossFailbackAccessKeyId = "ENC:A256GCM:v1:k1:fb-id",
            ossFailbackAccessKeySecret = "ENC:A256GCM:v1:k1:fb-secret",
        )
        val port = FakeAdminNodePort(existing)
        val controller = AdminNodeController(port, NoopAuditLogPort)

        controller.updateNode(
            request = adminRequest(),
            id = existing.id!!,
            body = UpdateNodeRequest(name = "Updated"),
        )

        val saved = port.saved.single()
        assertEquals("https://primary-bucket.oss-cn-hongkong.aliyuncs.com", saved.ossPublicEndpoint)
        assertEquals("https://fallback-bucket.oss-cn-hongkong.aliyuncs.com", saved.ossFailbackEndpoint)
        assertEquals("ENC:A256GCM:v1:k1:id", saved.ossAccessKeyId)
        assertEquals("ENC:A256GCM:v1:k1:secret", saved.ossAccessKeySecret)
        assertEquals("ENC:A256GCM:v1:k1:fb-id", saved.ossFailbackAccessKeyId)
        assertEquals("ENC:A256GCM:v1:k1:fb-secret", saved.ossFailbackAccessKeySecret)
    }

    @Test
    fun updateNodeClearsBlankOssEndpoints() {
        val existing = serverNode(
            ossPublicEndpoint = "https://primary-bucket.oss-cn-hongkong.aliyuncs.com",
            ossFailbackEndpoint = "https://fallback-bucket.oss-cn-hongkong.aliyuncs.com",
            ossAccessKeyId = "ENC:A256GCM:v1:k1:id",
            ossAccessKeySecret = "ENC:A256GCM:v1:k1:secret",
            ossFailbackAccessKeyId = "ENC:A256GCM:v1:k1:fb-id",
            ossFailbackAccessKeySecret = "ENC:A256GCM:v1:k1:fb-secret",
        )
        val port = FakeAdminNodePort(existing)
        val controller = AdminNodeController(port, NoopAuditLogPort)

        controller.updateNode(
            request = adminRequest(),
            id = existing.id!!,
            body = UpdateNodeRequest(
                ossPublicEndpoint = "",
                ossFailbackEndpoint = "   ",
                ossAccessKeyId = "",
                ossAccessKeySecret = "   ",
                ossFailbackAccessKeyId = "",
                ossFailbackAccessKeySecret = "   ",
            ),
        )

        val saved = port.saved.single()
        assertNull(saved.ossPublicEndpoint)
        assertNull(saved.ossFailbackEndpoint)
        assertNull(saved.ossAccessKeyId)
        assertNull(saved.ossAccessKeySecret)
        assertNull(saved.ossFailbackAccessKeyId)
        assertNull(saved.ossFailbackAccessKeySecret)
    }

    @Test
    fun createNodeRejectsInvalidOssEndpointRoots() {
        val controller = AdminNodeController(FakeAdminNodePort(), NoopAuditLogPort)

        assertFailsWith<com.layababateam.xinxiwang_backend.exception.BusinessException> {
            controller.createNode(
                request = adminRequest(),
                body = CreateNodeRequest(
                    name = "Singapore",
                    appServerUrl = "https://sg.example.com/appserver",
                    websocketUrl = "wss://sg.example.com/websocket",
                    baseUrl = "https://sg.example.com",
                    region = "international",
                    ossPublicEndpoint = "ftp://primary-bucket.oss-cn-hongkong.aliyuncs.com",
                ),
            )
        }
        assertFailsWith<com.layababateam.xinxiwang_backend.exception.BusinessException> {
            controller.createNode(
                request = adminRequest(),
                body = CreateNodeRequest(
                    name = "Singapore",
                    appServerUrl = "https://sg.example.com/appserver",
                    websocketUrl = "wss://sg.example.com/websocket",
                    baseUrl = "https://sg.example.com",
                    region = "international",
                    ossFailbackEndpoint = "https://primary-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json",
                ),
            )
        }
        assertFailsWith<com.layababateam.xinxiwang_backend.exception.BusinessException> {
            controller.createNode(
                request = adminRequest(),
                body = CreateNodeRequest(
                    name = "Singapore",
                    appServerUrl = "https://sg.example.com/appserver",
                    websocketUrl = "wss://sg.example.com/websocket",
                    baseUrl = "https://sg.example.com",
                    region = "international",
                    ossPublicEndpoint = "https://cdn.example.com",
                ),
            )
        }
    }

    @Test
    fun createNodeRejectsPartialOssCredentialPairs() {
        val controller = AdminNodeController(FakeAdminNodePort(), NoopAuditLogPort)

        assertFailsWith<com.layababateam.xinxiwang_backend.exception.BusinessException> {
            controller.createNode(
                request = adminRequest(),
                body = CreateNodeRequest(
                    name = "Singapore",
                    appServerUrl = "https://sg.example.com/appserver",
                    websocketUrl = "wss://sg.example.com/websocket",
                    baseUrl = "https://sg.example.com",
                    region = "international",
                    ossPublicEndpoint = "https://primary-bucket.oss-cn-hongkong.aliyuncs.com",
                    ossAccessKeyId = "ENC:A256GCM:v1:k1:id",
                ),
            )
        }
    }
}

private fun serverNode(
    ossPublicEndpoint: String? = null,
    ossFailbackEndpoint: String? = null,
    ossAccessKeyId: String? = null,
    ossAccessKeySecret: String? = null,
    ossFailbackAccessKeyId: String? = null,
    ossFailbackAccessKeySecret: String? = null,
): ServerNode = ServerNode(
    id = "node-1",
    name = "Node",
    appServerUrl = "https://node.example.com/appserver",
    websocketUrl = "wss://node.example.com/websocket",
    baseUrl = "https://node.example.com",
    region = "international",
    ossPublicEndpoint = ossPublicEndpoint,
    ossFailbackEndpoint = ossFailbackEndpoint,
    ossAccessKeyId = ossAccessKeyId,
    ossAccessKeySecret = ossAccessKeySecret,
    ossFailbackAccessKeyId = ossFailbackAccessKeyId,
    ossFailbackAccessKeySecret = ossFailbackAccessKeySecret,
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
