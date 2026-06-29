package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.middleware.AdminAuthInterceptor
import com.layababateam.xinxiwang_backend.middleware.ClientAuthInterceptor
import com.layababateam.xinxiwang_backend.service.AppEntryPort
import com.layababateam.xinxiwang_backend.service.SdkAppEntryRequest
import com.layababateam.xinxiwang_backend.service.SdkAppEntryToggleRequest
import com.layababateam.xinxiwang_backend.service.SdkH5SessionExchangeRequest
import com.layababateam.xinxiwang_backend.service.SdkH5TicketRequest
import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdkAppEntryControllerTest {
    @Test
    fun openListDelegatesPlacementPlatformAndVersionToPort() {
        val port = FakeAppEntryPort()
        val controller = SdkOpenAppEntryController(port)

        val response = controller.list(
            placement = "home",
            platform = "android",
            version = "1.2.3",
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(listOf(mapOf("entryKey" to "docs")), response.body!!.data)
        assertEquals(OpenEntriesCall("home", "android", "1.2.3"), port.openEntriesCalls.single())
    }

    @Test
    fun createTicketUsesAuthenticatedClientContext() {
        val port = FakeAppEntryPort()
        val controller = SdkH5TicketController(port)

        val response = controller.createTicket(
            request = request(
                attributes = mapOf(
                    ClientAuthInterceptor.USER_ID_ATTR to "user-1",
                    ClientAuthInterceptor.CLIENT_PLATFORM_ATTR to "ios",
                ),
                headers = mapOf("X-App-Version" to "4.5.6"),
            ),
            body = SdkH5TicketRequest(entryKey = "resource-library", node = "sg"),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(mapOf("ticket" to "ticket-1"), response.body!!.data)
        assertEquals(
            H5TicketCall(
                userId = "user-1",
                entryKey = "resource-library",
                platform = "ios",
                clientVersion = "4.5.6",
                node = "sg",
            ),
            port.h5TicketCalls.single(),
        )
    }

    @Test
    fun exchangeReturnsUnauthorizedWhenTicketIsInvalid() {
        val port = FakeAppEntryPort(exchangeResult = null)
        val controller = SdkH5TicketController(port)

        val response = controller.exchange(SdkH5SessionExchangeRequest(ticket = "expired"))

        assertEquals(401, response.statusCode.value())
        assertFalse(response.body!!.success)
        assertEquals("ticket无效或已过期", response.body!!.message)
        assertNull(response.body!!.data)
        assertEquals(listOf("expired"), port.exchangeTicketCalls)
    }

    @Test
    fun adminCreateAndToggleUseAdminUsername() {
        val port = FakeAppEntryPort()
        val controller = SdkAdminAppEntryController(port)
        val request = request(
            attributes = mapOf(AdminAuthInterceptor.ADMIN_USERNAME_ATTR to "root"),
        )

        controller.create(
            request = request,
            body = SdkAppEntryRequest(entryKey = "docs", placement = "home"),
        )
        controller.toggle(
            request = request,
            id = "entry-1",
            body = SdkAppEntryToggleRequest(enabled = false),
        )

        assertEquals(SaveEntryCall(id = null, updatedBy = "root", entryKey = "docs"), port.saveEntryCalls.single())
        assertEquals(ToggleEntryCall(id = "entry-1", enabled = false, updatedBy = "root"), port.toggleCalls.single())
    }
}

private data class OpenEntriesCall(val placement: String, val platform: String, val version: String?)

private data class H5TicketCall(
    val userId: String,
    val entryKey: String,
    val platform: String?,
    val clientVersion: String?,
    val node: String?,
)

private data class SaveEntryCall(val id: String?, val updatedBy: String, val entryKey: String)

private data class ToggleEntryCall(val id: String, val enabled: Boolean, val updatedBy: String)

private class FakeAppEntryPort(
    private val exchangeResult: Any? = mapOf("userId" to "user-1"),
) : AppEntryPort {
    val openEntriesCalls = mutableListOf<OpenEntriesCall>()
    val h5TicketCalls = mutableListOf<H5TicketCall>()
    val exchangeTicketCalls = mutableListOf<String>()
    val saveEntryCalls = mutableListOf<SaveEntryCall>()
    val toggleCalls = mutableListOf<ToggleEntryCall>()

    override fun listAdminEntries(placement: String?, enabled: Boolean?): List<Any> =
        listOf(mapOf("placement" to placement, "enabled" to enabled))

    override fun saveEntry(input: SdkAppEntryRequest, id: String?, updatedBy: String): Any {
        saveEntryCalls += SaveEntryCall(id = id, updatedBy = updatedBy, entryKey = input.entryKey)
        return mapOf("id" to (id ?: "entry-1"))
    }

    override fun toggleEntry(id: String, enabled: Boolean, updatedBy: String): Any {
        toggleCalls += ToggleEntryCall(id = id, enabled = enabled, updatedBy = updatedBy)
        return mapOf("id" to id, "enabled" to enabled)
    }

    override fun deleteEntry(id: String) = Unit

    override fun listOpenEntries(placement: String, platform: String, version: String?): List<Any> {
        openEntriesCalls += OpenEntriesCall(placement, platform, version)
        return listOf(mapOf("entryKey" to "docs"))
    }

    override fun createH5Ticket(
        userId: String,
        entryKey: String,
        platform: String?,
        clientVersion: String?,
        node: String?,
    ): Any {
        h5TicketCalls += H5TicketCall(userId, entryKey, platform, clientVersion, node)
        return mapOf("ticket" to "ticket-1")
    }

    override fun exchangeH5Ticket(ticket: String): Any? {
        exchangeTicketCalls += ticket
        return exchangeResult
    }
}

private fun request(
    attributes: Map<String, Any?> = emptyMap(),
    headers: Map<String, String?> = emptyMap(),
): HttpServletRequest =
    Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getAttribute" -> attributes[args?.firstOrNull()]
            "getHeader" -> headers[args?.firstOrNull()]
            else -> defaultSdkControllerValue(method.returnType)
        }
    } as HttpServletRequest

private fun defaultSdkControllerValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        else -> null
    }
