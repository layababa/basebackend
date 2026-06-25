package com.layababateam.xinxiwang_backend.config

import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushDaConfigTest {
    @Test
    fun `pushDa RestTemplate has connect and read timeouts`() {
        val config = PushDaConfig().apply {
            connectTimeoutMs = CONNECT_MS
            readTimeoutMs = READ_MS
        }

        val factory = config.pushDaRestTemplate().requestFactory

        assertTrue(factory is SimpleClientHttpRequestFactory)
        assertEquals(CONNECT_MS, timeoutMillis(factory, "connectTimeout"))
        assertEquals(READ_MS, timeoutMillis(factory, "readTimeout"))
    }

    private fun timeoutMillis(target: Any, name: String): Long {
        val value = SimpleClientHttpRequestFactory::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(target)
        return when (value) {
            is Duration -> value.toMillis()
            is Number -> value.toLong()
            else -> error("Unsupported timeout field type: ${value?.javaClass?.name}")
        }
    }

    private companion object {
        const val CONNECT_MS = 3_000L
        const val READ_MS = 5_000L
    }
}
