package com.layababateam.xinxiwang_backend.netty

import com.layababateam.xinxiwang_backend.repository.ClientVersionRuleRepository
import com.layababateam.xinxiwang_backend.service.ClientUpdatePolicyService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NettyWebSocketHandlerUpdatePolicyTest {
    @Test
    fun `websocket auth uses shared client update policy service`() {
        val constructorTypes = NettyWebSocketHandler::class.constructors
            .single()
            .parameters
            .map { it.type.classifier }

        assertTrue(ClientUpdatePolicyService::class in constructorTypes)
        assertFalse(ClientVersionRuleRepository::class in constructorTypes)
    }
}
