package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.model.WebCustomerServiceEntry
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import com.layababateam.xinxiwang_backend.service.UploadPort
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceService
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceTokenService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import java.lang.reflect.Proxy
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicWebCustomerServiceDefaultEntryControllerTest {
    @Test
    fun defaultBootstrapSkipsForbiddenEntriesAndReturnsFirstAccessibleEntry() {
        val service = webCustomerServiceService(
            entries = listOf(
                entry("blocked", allowedDomains = listOf("blocked.example.com"), createdAt = 20),
                entry("allowed", allowedDomains = listOf("app.example.com"), createdAt = 10),
            ),
        )
        val controller = PublicWebCustomerServiceDefaultEntryController(service)

        val response = controller.defaultBootstrap(request(origin = "https://app.example.com"))

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        val data = response.body!!.data!!
        assertEquals("allowed", data.javaClass.methods.first { it.name == "getEntryId" }.invoke(data))
    }

    @Test
    fun defaultBootstrapReturns404WhenNoEnabledEntryIsAccessible() {
        val service = webCustomerServiceService(
            entries = listOf(
                entry("disabled", enabled = false, allowedDomains = listOf("app.example.com")),
                entry("blocked", allowedDomains = listOf("blocked.example.com")),
            ),
        )
        val controller = PublicWebCustomerServiceDefaultEntryController(service)

        val response = controller.defaultBootstrap(request(origin = "https://app.example.com"))

        assertEquals(404, response.statusCode.value())
        assertFalse(response.body!!.success)
    }
}

private fun webCustomerServiceService(entries: List<WebCustomerServiceEntry>): WebCustomerServiceService =
    WebCustomerServiceService(
        entryRepository = entryRepository(entries),
        sessionRepository = unsupportedProxy(WebCustomerServiceSessionRepository::class.java),
        messageRepository = unsupportedProxy(WebCustomerServiceMessageRepository::class.java),
        tokenService = WebCustomerServiceTokenService(),
        uploadPort = unsupportedProxy(UploadPort::class.java),
        mongoTemplate = mongoTemplate(),
    )

private fun entry(
    id: String,
    enabled: Boolean = true,
    allowedDomains: List<String>,
    createdAt: Long = 0,
) = WebCustomerServiceEntry(
    id = id,
    name = "Entry $id",
    enabled = enabled,
    allowedDomains = allowedDomains,
    seatAdminIds = emptyList(),
    welcomeMessage = "Welcome",
    themeColor = "#2563eb",
    createdBy = "admin",
    createdAt = createdAt,
    updatedAt = createdAt,
)

private fun entryRepository(entries: List<WebCustomerServiceEntry>): WebCustomerServiceEntryRepository =
    Proxy.newProxyInstance(
        WebCustomerServiceEntryRepository::class.java.classLoader,
        arrayOf(WebCustomerServiceEntryRepository::class.java),
    ) { _, method, args ->
        when (method.name) {
            "findAllByOrderByCreatedAtDesc" -> entries.sortedByDescending { it.createdAt }
            "findById" -> Optional.ofNullable(entries.firstOrNull { it.id == args?.firstOrNull() })
            else -> defaultValue(method.returnType)
        }
    } as WebCustomerServiceEntryRepository

private fun mongoTemplate(): MongoTemplate {
    val factory = unsupportedProxy(MongoDatabaseFactory::class.java)
    return MongoTemplate(factory)
}

private fun request(origin: String): HttpServletRequest =
    Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getHeader" -> when (args?.firstOrNull()) {
                "Origin" -> origin
                "Referer" -> null
                else -> null
            }
            else -> defaultValue(method.returnType)
        }
    } as HttpServletRequest

private fun <T> unsupportedProxy(type: Class<T>): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        defaultValue(method.returnType)
    } as T

private fun defaultValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        java.util.Optional::class.java -> Optional.empty<Any>()
        List::class.java -> emptyList<Any>()
        else -> null
    }
