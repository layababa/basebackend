package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.dto.ConversationDto
import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.CallRoomProbeSession
import com.layababateam.xinxiwang_backend.service.CallRoomProbeSessionPort
import com.layababateam.xinxiwang_backend.service.ConversationInfoPort
import com.layababateam.xinxiwang_backend.service.TrtcRoomUsersPort
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RentmsgWsHandlerCompatibilityTest {
    @Test
    fun `get conversation info returns SDK conversation payload for current device`() {
        val calls = mutableListOf<Triple<String, String, String?>>()
        val dto = ConversationDto(
            id = "conv-1",
            type = 0,
            peerUserId = "peer-1",
            peerUserName = "Peer",
            createdAt = 100,
            myDeviceReadSeqId = 9,
        )
        val captured = mutableListOf<Map<String, Any?>>()
        val handler = GetConversationInfoHandler(
            conversationInfoPort = object : ConversationInfoPort {
                override fun getConversationInfo(
                    userId: String,
                    conversationId: String,
                    requesterDeviceId: String?,
                ): ConversationDto? {
                    calls += Triple(userId, conversationId, requesterDeviceId)
                    return dto
                }
            },
            wsResponseSender = capturingSender(captured),
            channelDeviceResolver = fixedDeviceResolver("device-a"),
        )

        handler.handle(proxyContext(), "user-1", mapOf("conversationId" to "conv-1"))

        val expectedCall: Triple<String, String, String?> = Triple("user-1", "conv-1", "device-a")
        assertEquals(listOf(expectedCall), calls)
        assertEquals("conversation_info_response", captured.single()["type"])
        assertEquals(dto, captured.single()["data"])
    }

    @Test
    fun `get conversation info keeps missing conversation response contract`() {
        val captured = mutableListOf<Map<String, Any?>>()
        val handler = GetConversationInfoHandler(
            conversationInfoPort = object : ConversationInfoPort {
                override fun getConversationInfo(
                    userId: String,
                    conversationId: String,
                    requesterDeviceId: String?,
                ): ConversationDto? = null
            },
            wsResponseSender = capturingSender(captured),
            channelDeviceResolver = fixedDeviceResolver(null),
        )

        handler.handle(proxyContext(), "user-1", mapOf("conversationId" to "missing"))

        val response = captured.single()
        assertEquals("conversation_info_response", response["type"])
        assertNull(response["data"])
        assertEquals("\u4f1a\u8bdd\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u8bbf\u95ee", response["error"])
    }

    @Test
    fun `call room probe reports active room membership`() {
        val captured = mutableListOf<Map<String, Any?>>()
        val handler = CallRoomProbeHandler(
            callRoomProbeSessionPort = object : CallRoomProbeSessionPort {
                override fun getCallRoomProbeSession(roomId: Int): CallRoomProbeSession? =
                    CallRoomProbeSession(
                        roomId = roomId,
                        callerId = "caller",
                        calleeId = "callee",
                        answered = true,
                    )
            },
            trtcRoomUsersPort = object : TrtcRoomUsersPort {
                override fun activeRoomUsers(roomId: Int): List<String>? = listOf("caller")
            },
            wsResponseSender = capturingSender(captured),
        )

        handler.handle(proxyContext(), "caller", mapOf("roomId" to 42))

        val data = captured.single()["data"] as Map<*, *>
        assertEquals("call_room_probe_response", captured.single()["type"])
        assertEquals(42, data["roomId"])
        assertEquals(true, data["sessionExists"])
        assertEquals(true, data["belongsToSession"])
        assertEquals(true, data["answered"])
        assertEquals("callee", data["peerId"])
        assertEquals(true, data["selfInRoom"])
        assertEquals(false, data["peerInRoom"])
        assertEquals("ok", data["probeStatus"])
    }

    @Test
    fun `call room probe rejects non members without exposing peer state`() {
        val captured = mutableListOf<Map<String, Any?>>()
        var queriedRoomUsers = false
        val handler = CallRoomProbeHandler(
            callRoomProbeSessionPort = object : CallRoomProbeSessionPort {
                override fun getCallRoomProbeSession(roomId: Int): CallRoomProbeSession? =
                    CallRoomProbeSession(roomId, callerId = "caller", calleeId = "callee", answered = false)
            },
            trtcRoomUsersPort = object : TrtcRoomUsersPort {
                override fun activeRoomUsers(roomId: Int): List<String>? {
                    queriedRoomUsers = true
                    return emptyList()
                }
            },
            wsResponseSender = capturingSender(captured),
        )

        handler.handle(proxyContext(), "intruder", mapOf("roomId" to 42))

        val data = captured.single()["data"] as Map<*, *>
        assertEquals(false, queriedRoomUsers)
        assertEquals(42, data["roomId"])
        assertEquals(true, data["sessionExists"])
        assertEquals(false, data["belongsToSession"])
        assertEquals("unavailable", data["probeStatus"])
    }

    @Test
    fun `call room probe keeps invalid room fallback response`() {
        val captured = mutableListOf<Map<String, Any?>>()
        val handler = CallRoomProbeHandler(
            callRoomProbeSessionPort = object : CallRoomProbeSessionPort {
                override fun getCallRoomProbeSession(roomId: Int): CallRoomProbeSession? = null
            },
            trtcRoomUsersPort = object : TrtcRoomUsersPort {
                override fun activeRoomUsers(roomId: Int): List<String>? = emptyList()
            },
            wsResponseSender = capturingSender(captured),
        )

        handler.handle(proxyContext(), "user-1", emptyMap())

        val data = captured.single()["data"] as Map<*, *>
        assertNull(data["roomId"])
        assertEquals(false, data["sessionExists"])
        assertEquals(false, data["belongsToSession"])
        assertEquals(false, data["answered"])
        assertEquals("unavailable", data["probeStatus"])
    }
}

private fun capturingSender(captured: MutableList<Map<String, Any?>>): WsResponseSender =
    object : WsResponseSender {
        override fun send(ctx: ChannelHandlerContext, data: Map<String, Any?>) {
            captured += data
        }
    }

private fun fixedDeviceResolver(deviceId: String?): ChannelDeviceResolver =
    object : ChannelDeviceResolver {
        override fun getDeviceId(channel: Channel): String? = deviceId
    }

private fun proxyContext(): ChannelHandlerContext {
    val channel = Proxy.newProxyInstance(
        Channel::class.java.classLoader,
        arrayOf(Channel::class.java),
    ) { _, _, _ -> null } as Channel
    return Proxy.newProxyInstance(
        ChannelHandlerContext::class.java.classLoader,
        arrayOf(ChannelHandlerContext::class.java),
    ) { _, method, _ ->
        if (method.name == "channel") channel else null
    } as ChannelHandlerContext
}
