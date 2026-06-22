package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.GroupOperationPort
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

class GroupVirtualMemberHandlerTest {
    @Test
    fun setVirtualMemberCountDelegatesAndReturnsOwnerViewMembers() {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val virtualMembers = listOf(
            mapOf(
                "userId" to "virtual:alice",
                "displayName" to "Alice",
                "avatarUrl" to null,
                "role" to 0,
                "isVirtual" to true,
            ),
        )
        val port = proxyGroupOperationPort { method, args ->
            if (method == "setVirtualMemberCount") {
                calls += Triple(args[0] as String, args[1] as String, args[2] as Int)
                virtualMembers
            } else {
                error("Unexpected GroupOperationPort call: $method")
            }
        }
        val captured = mutableListOf<String>()
        val ctx = capturingContext(captured)
        val handler = GroupSetVirtualMemberCountHandler(
            port,
            GroupOperationResponseSender(JsonMapper.builder().build()),
        )

        handler.handle(ctx, "owner-1", mapOf("conversationId" to "group-1", "count" to 4))

        assertEquals(listOf(Triple("owner-1", "group-1", 4)), calls)
        assertEquals(1, captured.size)
        val response = JsonMapper.builder().build().readValue(captured.single(), Map::class.java)
        assertEquals("group_set_virtual_member_count_success", response["type"])
        assertEquals("group-1", response["conversationId"])
        assertEquals(1, response["virtualMemberCount"])
        val members = response["data"] as List<*>
        assertEquals("virtual:alice", (members.single() as Map<*, *>)["userId"])
        assertTrue((members.single() as Map<*, *>)["isVirtual"] as Boolean)
    }

    @Test
    fun setVirtualMemberCountCountsSnakeCaseNumericVirtualFlags() {
        val port = proxyGroupOperationPort { method, _ ->
            if (method == "setVirtualMemberCount") {
                listOf(
                    mapOf("userId" to "real-user", "displayName" to "Real", "is_virtual" to 0),
                    mapOf("userId" to "virtual:bob", "displayName" to "Bob", "is_virtual" to 1),
                )
            } else {
                error("Unexpected GroupOperationPort call: $method")
            }
        }
        val captured = mutableListOf<String>()
        val handler = GroupSetVirtualMemberCountHandler(
            port,
            GroupOperationResponseSender(JsonMapper.builder().build()),
        )

        handler.handle(capturingContext(captured), "owner-1", mapOf("conversationId" to "group-1", "count" to 1))

        val response = JsonMapper.builder().build().readValue(captured.single(), Map::class.java)
        assertEquals(1, response["virtualMemberCount"])
    }
}

private fun proxyGroupOperationPort(handler: (String, Array<Any?>) -> Any?): GroupOperationPort =
    Proxy.newProxyInstance(
        GroupOperationPort::class.java.classLoader,
        arrayOf(GroupOperationPort::class.java),
    ) { _, method, args ->
        handler(method.name, args ?: emptyArray())
    } as GroupOperationPort

private fun capturingContext(captured: MutableList<String>): ChannelHandlerContext =
    Proxy.newProxyInstance(
        ChannelHandlerContext::class.java.classLoader,
        arrayOf(ChannelHandlerContext::class.java),
    ) { _, method, args ->
        if (method.name == "writeAndFlush") {
            val frame = args?.firstOrNull() as TextWebSocketFrame
            captured += frame.text()
        }
        null
    } as ChannelHandlerContext
