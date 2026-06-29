package com.layababateam.xinxiwang_backend.config

import kotlin.test.Test
import kotlin.test.assertNotNull

class CorsConfigTest {
    @Test
    fun `corsFilter creates Spring CorsFilter bean`() {
        val filter = CorsConfig().corsFilter()

        assertNotNull(filter)
    }
}
