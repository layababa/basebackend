package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebCustomerServiceTokenServiceTest {

    @Test
    fun `signed visitor token round trips claims`() {
        val service = WebCustomerServiceTokenService(
            configuredSecret = "unit-test-secret-unit-test-secret",
            activeProfiles = "",
        )

        val token = service.sign(
            entryId = "entry-1",
            sessionId = "session-1",
            visitorId = "visitor-1",
            ttlMillis = 60_000,
            nowMillis = 1_000,
        )

        val claims = service.verify(token, nowMillis = 2_000)
        assertEquals("entry-1", claims.entryId)
        assertEquals("session-1", claims.sessionId)
        assertEquals("visitor-1", claims.visitorId)
    }

    @Test
    fun `visitor token rejects expired token`() {
        val service = WebCustomerServiceTokenService(
            configuredSecret = "unit-test-secret-unit-test-secret",
            activeProfiles = "",
        )

        val token = service.sign(
            entryId = "entry-1",
            sessionId = "session-1",
            visitorId = "visitor-1",
            ttlMillis = 1_000,
            nowMillis = 1_000,
        )

        assertFailsWith<IllegalArgumentException> {
            service.verify(token, nowMillis = 3_000)
        }
    }

    @Test
    fun `staging and prod require configured token secret`() {
        assertFailsWith<IllegalStateException> {
            WebCustomerServiceTokenService(
                configuredSecret = "",
                activeProfiles = "staging",
            )
        }
        assertFailsWith<IllegalStateException> {
            WebCustomerServiceTokenService(
                configuredSecret = "",
                activeProfiles = "prod",
            )
        }
    }
}
