package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertTrue

class PullLogReportServiceTest {
    @Test
    fun `pull log report service implements report port`() {
        assertTrue(PullLogReportPort::class.java.isAssignableFrom(PullLogReportService::class.java))
    }
}
