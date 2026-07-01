package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerServiceQrAssignmentRulesTest {

    @Test
    fun `selects enabled binding with lowest assigned count then sort order then creation time`() {
        val selected = CustomerServiceQrAssignmentRules.selectBinding(
            listOf(
                CustomerServiceQrCandidate("b3", "cs3", assignedCount = 2, sortOrder = 0, createdAt = 10),
                CustomerServiceQrCandidate("b2", "cs2", assignedCount = 1, sortOrder = 2, createdAt = 10),
                CustomerServiceQrCandidate("b1", "cs1", assignedCount = 1, sortOrder = 1, createdAt = 20),
                CustomerServiceQrCandidate("b4", "cs4", assignedCount = 1, sortOrder = 1, createdAt = 5),
            ),
        )

        assertEquals("b4", selected?.bindingId)
    }

    @Test
    fun `ignores disabled bindings and disabled accounts`() {
        val selected = CustomerServiceQrAssignmentRules.selectBinding(
            listOf(
                CustomerServiceQrCandidate("disabled-binding", "cs1", assignedCount = 0, sortOrder = 0, createdAt = 1, bindingEnabled = false),
                CustomerServiceQrCandidate("disabled-account", "cs2", assignedCount = 0, sortOrder = 0, createdAt = 2, accountEnabled = false),
                CustomerServiceQrCandidate("eligible", "cs3", assignedCount = 3, sortOrder = 0, createdAt = 3),
            ),
        )

        assertEquals("eligible", selected?.bindingId)
    }

    @Test
    fun `returns null when no eligible binding exists`() {
        val selected = CustomerServiceQrAssignmentRules.selectBinding(
            listOf(
                CustomerServiceQrCandidate("disabled-binding", "cs1", assignedCount = 0, sortOrder = 0, createdAt = 1, bindingEnabled = false),
                CustomerServiceQrCandidate("disabled-account", "cs2", assignedCount = 0, sortOrder = 0, createdAt = 2, accountEnabled = false),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun `builds customer service qr url from configured public base`() {
        val url = CustomerServiceQrAssignmentRules.buildQrUrl("https://chat.example.com/api/", "abc 123")

        assertEquals("https://chat.example.com/customerservice?=abc+123", url)
    }

    @Test
    fun `builds customer service qr landing url under admin route`() {
        val url = CustomerServiceQrAssignmentRules.buildLandingUrl("https://admin.example.com/admin/", "abc 123")

        assertEquals("https://admin.example.com/admin/customer-service/qr/abc+123", url)
    }
}
