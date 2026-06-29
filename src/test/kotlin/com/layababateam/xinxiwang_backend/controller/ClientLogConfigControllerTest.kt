package com.layababateam.xinxiwang_backend.controller

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientLogConfigControllerTest {
    @Test
    fun `update request carries critical log flag and optional expected revision`() {
        val request = ClientLogConfigUpdateRequest(
            criticalLogEnabled = false,
            expectedRevision = 7,
        )

        assertEquals(false, request.criticalLogEnabled)
        assertEquals(7, request.expectedRevision)
    }
}
