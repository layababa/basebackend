package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.model.WebCustomerServiceEntry
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import com.layababateam.xinxiwang_backend.service.UploadPort
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceService
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceTokenService
import java.lang.reflect.Proxy
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminWebCustomerServiceEntryDeleteControllerTest {
    @Test
    fun deleteEntryRemovesExistingEntry() {
        val entries = linkedMapOf("entry-1" to entry("entry-1"))
        val controller = AdminWebCustomerServiceController(
            webCustomerServiceServiceForDelete(entries),
        )

        val response = controller.deleteEntry("entry-1")

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertFalse(entries.containsKey("entry-1"))
    }

    @Test
    fun deleteEntryReturns404WhenEntryDoesNotExist() {
        val controller = AdminWebCustomerServiceController(
            webCustomerServiceServiceForDelete(linkedMapOf()),
        )

        val response = controller.deleteEntry("missing")

        assertEquals(404, response.statusCode.value())
        assertFalse(response.body!!.success)
    }
}

private fun webCustomerServiceServiceForDelete(
    entries: MutableMap<String, WebCustomerServiceEntry>,
): WebCustomerServiceService =
    WebCustomerServiceService(
        entryRepository = entryRepositoryForDelete(entries),
        sessionRepository = unsupportedDeleteProxy(WebCustomerServiceSessionRepository::class.java),
        messageRepository = unsupportedDeleteProxy(WebCustomerServiceMessageRepository::class.java),
        tokenService = WebCustomerServiceTokenService(),
        uploadPort = unsupportedDeleteProxy(UploadPort::class.java),
        mongoTemplate = unusedMongoTemplate(),
    )

private fun entry(id: String) = WebCustomerServiceEntry(
    id = id,
    name = "Entry $id",
    enabled = true,
    allowedDomains = listOf("app.example.com"),
    seatAdminIds = emptyList(),
    welcomeMessage = "Welcome",
    themeColor = "#2563eb",
    createdBy = "admin",
)

private fun entryRepositoryForDelete(
    entries: MutableMap<String, WebCustomerServiceEntry>,
): WebCustomerServiceEntryRepository =
    Proxy.newProxyInstance(
        WebCustomerServiceEntryRepository::class.java.classLoader,
        arrayOf(WebCustomerServiceEntryRepository::class.java),
    ) { _, method, args ->
        when (method.name) {
            "findById" -> Optional.ofNullable(entries[args?.firstOrNull() as String])
            "deleteById" -> {
                entries.remove(args?.firstOrNull() as String)
                Unit
            }
            "existsById" -> entries.containsKey(args?.firstOrNull() as String)
            "findAllByOrderByCreatedAtDesc" -> entries.values.toList()
            else -> deleteDefaultValue(method.returnType)
        }
    } as WebCustomerServiceEntryRepository

private fun <T> unsupportedDeleteProxy(type: Class<T>): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        deleteDefaultValue(method.returnType)
    } as T

private fun deleteDefaultValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        java.util.Optional::class.java -> Optional.empty<Any>()
        List::class.java -> emptyList<Any>()
        else -> null
    }
