package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.AddVirtualParticipantsRequest
import com.layababateam.xinxiwang_backend.dto.ParticipantDto
import com.layababateam.xinxiwang_backend.service.MeetingClientCompatibilityPort
import com.layababateam.xinxiwang_backend.service.MeetingPort
import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeetingControllerVirtualParticipantsTest {
    @Test
    fun setVirtualParticipantsDelegatesToMeetingPort() {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val expected = listOf(
            ParticipantDto(
                userId = "virtual:alice",
                displayName = "Alice",
                avatarUrl = null,
                isCreator = false,
                isVirtual = true,
            ),
        )
        val port = proxyMeetingPort { method, args ->
            if (method == "setVirtualParticipants") {
                calls += Triple(args[0] as String, args[1] as String, args[2] as Int)
                expected
            } else {
                error("Unexpected MeetingPort call: $method")
            }
        }
        val controller = MeetingController(port, alwaysCompatible())

        val response = controller.setVirtualParticipants(
            meetingId = "meeting-1",
            req = AddVirtualParticipantsRequest(count = 3),
            request = requestWithUserId("owner-1"),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals("已设置虚拟成员数量", response.body!!.message)
        assertEquals(expected, response.body!!.data)
        assertEquals(listOf(Triple("owner-1", "meeting-1", 3)), calls)
    }
}

private fun proxyMeetingPort(handler: (String, Array<Any?>) -> Any?): MeetingPort =
    Proxy.newProxyInstance(
        MeetingPort::class.java.classLoader,
        arrayOf(MeetingPort::class.java),
    ) { _, method, args ->
        handler(method.name, args ?: emptyArray())
    } as MeetingPort

private fun requestWithUserId(userId: String): HttpServletRequest =
    Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getAttribute" -> userId
            else -> when (method.returnType) {
                String::class.java -> ""
                Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
                Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
                Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
    } as HttpServletRequest

private fun alwaysCompatible(): MeetingClientCompatibilityPort =
    object : MeetingClientCompatibilityPort {
        override val meetingScheduleUpdateMessage: String = ""
        override fun supportsMeetingSchedule(request: HttpServletRequest): Boolean = true
    }
