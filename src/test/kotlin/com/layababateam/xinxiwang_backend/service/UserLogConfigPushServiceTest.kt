package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.UserLogConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class UserLogConfigPushServiceTest {
    @Test
    fun `push sends client log config payload to eligible devices`() {
        val port = RecordingPushPort(eligibleDeviceIds = setOf("d1", "d2"))
        val service = UserLogConfigPushService(port)
        val config = UserLogConfig(
            userId = "u1",
            criticalLogEnabled = false,
            revision = 2,
            updatedAt = 1234,
            ackedDeviceIds = setOf("d1"),
        )

        val delivered = service.pushToEligibleDevices("u1", config)
        val view = service.toView("u1", config)

        assertEquals(3, delivered)
        assertEquals(
            """{"type":"client_log_config_updated","data":{"criticalLogEnabled":false,"revision":2,"updatedAt":1234}}""",
            port.pushed.single().second,
        )
        assertEquals(2, view["eligibleOnlineDevices"])
        assertEquals(1, view["ackedDevices"])
    }

    private class RecordingPushPort(
        private val eligibleDeviceIds: Set<String>,
    ) : UserLogConfigPushPort {
        val pushed = mutableListOf<Pair<String, String>>()

        override fun pushClientLogConfigToEligibleUser(userId: String, message: String): Int {
            pushed += userId to message
            return 3
        }

        override fun getEligibleClientLogDeviceIds(userId: String): Set<String> = eligibleDeviceIds
    }
}
