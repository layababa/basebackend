package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebCustomerServiceRulesTest {

    @Test
    fun `entry validation rejects empty domains`() {
        assertFalse(WebCustomerServiceRules.isValidAllowedDomains(emptyList()))
        assertFalse(WebCustomerServiceRules.isValidAllowedDomains(listOf("  ")))
    }

    @Test
    fun `domain matcher accepts exact and wildcard hosts`() {
        val domains = listOf("example.com", "*.service.test")

        assertTrue(WebCustomerServiceRules.isOriginAllowed("https://example.com/chat", domains))
        assertTrue(WebCustomerServiceRules.isOriginAllowed("https://help.service.test", domains))
        assertTrue(WebCustomerServiceRules.isOriginAllowed("https://a.b.service.test", domains))
        assertFalse(WebCustomerServiceRules.isOriginAllowed("https://service.test", domains))
        assertFalse(WebCustomerServiceRules.isOriginAllowed("https://evil-example.com", domains))
    }

    @Test
    fun `theme color accepts only hex rgb`() {
        assertTrue(WebCustomerServiceRules.isValidThemeColor("#2563eb"))
        assertFalse(WebCustomerServiceRules.isValidThemeColor("2563eb"))
        assertFalse(WebCustomerServiceRules.isValidThemeColor("#xyzxyz"))
        assertFalse(WebCustomerServiceRules.isValidThemeColor("#1234"))
    }
}
