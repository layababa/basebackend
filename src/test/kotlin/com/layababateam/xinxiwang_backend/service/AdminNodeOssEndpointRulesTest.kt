package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.exception.BusinessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdminNodeOssEndpointRulesTest {
    @Test
    fun normalizesBlankAndTrailingSlash() {
        assertNull(AdminNodeOssEndpointRules.normalizeOptionalRootUrl(null, "OSS main endpoint"))
        assertNull(AdminNodeOssEndpointRules.normalizeOptionalRootUrl("   ", "OSS main endpoint"))
        assertEquals(
            "https://main-bucket.oss-cn-hongkong.aliyuncs.com",
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl(
                " https://main-bucket.oss-cn-hongkong.aliyuncs.com/// ",
                "OSS main endpoint",
            ),
        )
    }

    @Test
    fun rejectsNonHttpConfigJsonAndCustomDomainUrls() {
        assertFailsWith<BusinessException> {
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl("ftp://oss.example.com", "OSS main endpoint")
        }
        assertFailsWith<BusinessException> {
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl(
                "https://main-bucket.oss-cn-hongkong.aliyuncs.com/config/cdn.json",
                "OSS main endpoint",
            )
        }
        assertFailsWith<BusinessException> {
            AdminNodeOssEndpointRules.normalizeOptionalRootUrl("https://media.example.com", "OSS main endpoint")
        }
    }
}
