package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals

class BotServiceCompatibilityTest {
    @Test
    fun `bot service is provided by sdk with create result contract`() {
        assertEquals("BotService", BotService::class.java.simpleName)
        assertEquals("BotCreateResult", BotService.BotCreateResult::class.java.simpleName)
    }
}
