package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.controller.unusedMongoTemplate
import com.layababateam.xinxiwang_backend.repository.AppLatestVersionRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientUpdatePolicyServiceTest {
    @Test
    fun `version rules compare semantic version before build suffix`() {
        assertTrue(ClientVersionRules.compareVersions("1.2.0+1", "1.1.9+999") > 0)
        assertTrue(ClientVersionRules.compareVersions("1.2.0+10", "1.2.0+9") > 0)
        assertEquals(0, ClientVersionRules.compareVersions("1.2.0", "1.2.0+0"))
    }

    @Test
    fun `specific version rule without build matches all builds of that semantic version`() {
        assertTrue(ClientVersionRules.specificVersionMatches("1.2.0+9", "1.2.0"))
        assertTrue(ClientVersionRules.specificVersionMatches("1.2.0+9", "1.2.0+9"))
        assertFalse(ClientVersionRules.specificVersionMatches("1.2.0+9", "1.2.0+10"))
        assertFalse(ClientVersionRules.specificVersionMatches("1.2.1+1", "1.2.0"))
    }

    @Test
    fun `force update payload exposes in app update and match reason`() {
        val service = ClientUpdatePolicyService(
            mongoTemplate = unusedMongoTemplate(),
            appLatestVersionRepository = appLatestVersionRepository(),
        )

        val payload = service.forceUpdatePayload(
            ClientUpdateDecision(
                hasUpdate = true,
                forceUpdate = true,
                inAppUpdate = false,
                updateUrl = "https://download.example.com/app.apk",
                currentVersion = "1.1.0+8",
                platform = "android",
                minVersion = "1.2.0+9",
                lessThanVersion = "1.2.0+9",
                specificVersions = listOf("1.1.0"),
                matchReason = "less_than_version",
            )
        )

        assertEquals("force_update", payload["type"])
        @Suppress("UNCHECKED_CAST")
        val data = payload["data"] as Map<String, Any?>
        assertEquals("https://download.example.com/app.apk", data["updateUrl"])
        assertEquals(false, data["inAppUpdate"])
        assertEquals("less_than_version", data["matchReason"])
        assertEquals(listOf("1.1.0"), data["specificVersions"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun appLatestVersionRepository(): AppLatestVersionRepository =
        Proxy.newProxyInstance(
            AppLatestVersionRepository::class.java.classLoader,
            arrayOf(AppLatestVersionRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "findByPlatform" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        } as AppLatestVersionRepository
}
