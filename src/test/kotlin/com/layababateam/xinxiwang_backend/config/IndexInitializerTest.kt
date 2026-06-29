package com.layababateam.xinxiwang_backend.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order

class IndexInitializerTest {
    @Test
    fun `index initializer runs after application ready before normal listeners`() {
        val method = IndexInitializer::class.java.getDeclaredMethod("initIndexes")

        assertTrue(method.isAnnotationPresent(EventListener::class.java))
        assertEquals(0, method.getAnnotation(Order::class.java).value)
    }
}
