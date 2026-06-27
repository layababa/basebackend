package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminGroupPort
import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminGroupVirtualMembersControllerTest {
    @Test
    fun setVirtualMembersDelegatesToAdminGroupPort() {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val members = listOf(
            mapOf("userId" to "real-user", "isVirtual" to false),
            mapOf("userId" to "virtual:alice", "isVirtual" to true),
        )
        val port = proxyAdminGroupPort { method, args ->
            if (method == "setVirtualMemberCount") {
                calls += Triple(args[0] as String, args[1] as String, args[2] as Int)
                members
            } else {
                error("Unexpected AdminGroupPort call: $method")
            }
        }
        val controller = AdminGroupController(port)

        val response = controller.setVirtualMembers(
            request = adminRequest("admin-1"),
            id = "group-1",
            body = AdminSetVirtualCountRequest(count = 1),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(listOf(Triple("admin-1", "group-1", 1)), calls)
        val data = response.body!!.data as Map<*, *>
        assertEquals(1, data["virtualMemberCount"])
        assertEquals(1, data["realMemberCount"])
        assertEquals(2, data["memberCount"])
        assertEquals(members, data["members"])
    }
}

private fun proxyAdminGroupPort(handler: (String, Array<Any?>) -> Any?): AdminGroupPort =
    Proxy.newProxyInstance(
        AdminGroupPort::class.java.classLoader,
        arrayOf(AdminGroupPort::class.java),
    ) { _, method, args ->
        handler(method.name, args ?: emptyArray())
    } as AdminGroupPort

private fun adminRequest(adminId: String): HttpServletRequest =
    Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getAttribute" -> when (args?.firstOrNull()) {
                "adminId" -> adminId
                else -> null
            }
            else -> defaultControllerValue(method.returnType)
        }
    } as HttpServletRequest

private fun defaultControllerValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        else -> null
    }
