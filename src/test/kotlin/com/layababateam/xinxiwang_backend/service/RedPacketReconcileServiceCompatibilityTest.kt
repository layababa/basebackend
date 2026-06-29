package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertTrue

class RedPacketReconcileServiceCompatibilityTest {
    @Test
    fun `red packet reconcile service implements admin reconcile port`() {
        assertTrue(AdminRedPacketReconcilePort::class.java.isAssignableFrom(RedPacketReconcileService::class.java))
    }
}
