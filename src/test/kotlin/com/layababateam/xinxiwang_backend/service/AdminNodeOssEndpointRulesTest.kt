package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.exception.BusinessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdminNodeOssEndpointRulesTest {
    @Test
    fun normalizesBlankAndTrailingSlash() {
        assertNull(AdminNodeOssEndpointRules.normalizeOptionalRootUrl(null, "OSS 主地址"))
        assertNull(AdminNodeOssEndpointRules.normalizeOptionalRootUrl("   ", "OSS 主地址"))
        assertEquals(
            "https://oss.example.com",
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl(" https://oss.example.com/// ", "OSS 主地址"),
        )
    }

    @Test
    fun rejectsNonHttpAndConfigJsonUrls() {
        assertFailsWith<BusinessException> {
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl("ftp://oss.example.com", "OSS 主地址")
        }
        assertFailsWith<BusinessException> {
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl(
                "https://oss.example.com/config/cdn.json",
                "OSS 主地址",
            )
        }
    }
}
