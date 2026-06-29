package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class RedPacketCacheServiceCompatibilityTest {
    @Test
    fun `red packet cache service and lua script are packaged by sdk`() {
        assertTrue(RedPacketCacheService::class.java.simpleName == "RedPacketCacheService")
        assertTrue(ClassPathResource("scripts/redpacket_claim.lua").exists())
    }
}
