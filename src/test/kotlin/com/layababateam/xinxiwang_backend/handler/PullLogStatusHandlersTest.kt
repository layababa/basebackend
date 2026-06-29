package com.layababateam.xinxiwang_backend.handler

import kotlin.test.Test
import kotlin.test.assertTrue

class PullLogStatusHandlersTest {
    @Test
    fun `pull log status handlers are SDK message handlers`() {
        val handlerClasses = listOf(
            "com.layababateam.xinxiwang_backend.handler.PullLogAckHandler",
            "com.layababateam.xinxiwang_backend.handler.PullLogDoneHandler",
            "com.layababateam.xinxiwang_backend.handler.PullLogFailedHandler",
        )

        handlerClasses.forEach { className ->
            assertTrue(MessageHandler::class.java.isAssignableFrom(Class.forName(className)))
        }
    }
}
