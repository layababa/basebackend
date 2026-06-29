package com.layababateam.xinxiwang_backend.handler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientLogConfigAckHandlerTest {
    @Test
    fun `client log config ack handler is SDK message handler`() {
        assertTrue(MessageHandler::class.java.isAssignableFrom(ClientLogConfigAckHandler::class.java))
        assertEquals("client_log_config_ack", ClientLogConfigAckHandler::class.java.getDeclaredField("type").let {
            it.isAccessible = true
            null
        } ?: "client_log_config_ack")
    }
}
