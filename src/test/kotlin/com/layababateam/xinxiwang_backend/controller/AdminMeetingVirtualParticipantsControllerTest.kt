package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ParticipantDto
import com.layababateam.xinxiwang_backend.service.AdminMeetingPort
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminMeetingVirtualParticipantsControllerTest {
    @Test
    fun setVirtualParticipantsDelegatesToAdminMeetingPort() {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val participants = listOf(
            ParticipantDto("real-user", "Real", null, isCreator = false),
            ParticipantDto("virtual:alice", "Alice", null, isCreator = false, isVirtual = true),
        )
        val port = proxyAdminMeetingPort { method, args ->
            if (method == "setVirtualParticipants") {
                calls += Triple(args[0] as String, args[1] as String, args[2] as Int)
                participants
            } else {
                error("Unexpected AdminMeetingPort call: $method")
            }
        }
        val controller = AdminMeetingController(port)

        val response = controller.setVirtualParticipants(
            request = adminMeetingRequest("admin-1"),
            id = "meeting-1",
            body = AdminSetVirtualCountRequest(count = 1),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(listOf(Triple("admin-1", "meeting-1", 1)), calls)
        val data = response.body!!.data as Map<*, *>
        assertEquals(1, data["virtualParticipantCount"])
        assertEquals(1, data["realParticipantCount"])
        assertEquals(2, data["participantCount"])
        assertEquals(participants, data["participants"])
    }
}

private fun proxyAdminMeetingPort(handler: (String, Array<Any?>) -> Any?): AdminMeetingPort =
    Proxy.newProxyInstance(
        AdminMeetingPort::class.java.classLoader,
        arrayOf(AdminMeetingPort::class.java),
    ) { _, method, args ->
        handler(method.name, args ?: emptyArray())
    } as AdminMeetingPort

private fun adminMeetingRequest(adminId: String): jakarta.servlet.http.HttpServletRequest =
    Proxy.newProxyInstance(
        jakarta.servlet.http.HttpServletRequest::class.java.classLoader,
        arrayOf(jakarta.servlet.http.HttpServletRequest::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getAttribute" -> when (args?.firstOrNull()) {
                "adminId" -> adminId
                else -> null
            }
            else -> when (method.returnType) {
                Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
                Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
                Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
                String::class.java -> ""
                else -> null
            }
        }
    } as jakarta.servlet.http.HttpServletRequest
