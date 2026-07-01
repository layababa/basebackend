package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals

class SalesmartlyExternalApiSignatureTest {
    @Test
    fun `signature uses token first sorted params and ignores external sign`() {
        val signature = SalesmartlyExternalApiSignature()
        val params = mapOf(
            "project_id" to "demo",
            "page" to 1,
            "external-sign" to "ignored",
            "from_channel_info" to "guest-1",
            "channel" to 3,
        )

        assertEquals(
            "demo-token&channel=3&from_channel_info=guest-1&page=1&project_id=demo",
            signature.payload("demo-token", params),
        )
        assertEquals("fdc2f7cca521f89050fa46dff272ae0f", signature.sign("demo-token", params))
    }
}
