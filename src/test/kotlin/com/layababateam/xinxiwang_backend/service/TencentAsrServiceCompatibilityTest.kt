package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertTrue

class TencentAsrServiceCompatibilityTest {
    @Test
    fun `tencent asr service implements sdk asr port`() {
        assertTrue(AsrPort::class.java.isAssignableFrom(TencentAsrService::class.java))
    }
}
