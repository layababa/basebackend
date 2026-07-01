package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomerServiceExternalApiSignatureTest {
    @Test
    fun `hmac signature covers method path query body timestamp and nonce`() {
        val signature = CustomerServiceExternalApiSignature()
        val body = """{"anonymousId":"visitor-1","content":"hello"}"""
        val signed = signature.sign(
            secret = "secret-1",
            method = "POST",
            path = "/api/external/customer-service/sessions",
            canonicalQuery = "entry=default",
            body = body,
            timestamp = "1782800000000",
            nonce = "nonce-1",
        )

        assertTrue(
            signature.matches(
                expected = signed,
                secret = "secret-1",
                method = "POST",
                path = "/api/external/customer-service/sessions",
                canonicalQuery = "entry=default",
                body = body,
                timestamp = "1782800000000",
                nonce = "nonce-1",
            ),
        )
        assertFalse(
            signature.matches(
                expected = signed,
                secret = "secret-1",
                method = "POST",
                path = "/api/external/customer-service/sessions",
                canonicalQuery = "entry=default",
                body = """{"anonymousId":"visitor-1","content":"changed"}""",
                timestamp = "1782800000000",
                nonce = "nonce-1",
            ),
        )
    }
}
