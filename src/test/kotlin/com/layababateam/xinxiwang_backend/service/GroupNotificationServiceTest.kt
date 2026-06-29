package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupNotificationServiceTest {
    @Test
    fun `sendSystemMessage sends content type 6 through port`() {
        val port = RecordingGroupSystemMessagePort()
        val service = GroupNotificationService(port)

        service.sendSystemMessage("operator", "conversation", "hello")

        assertEquals(
            listOf(SentGroupSystemMessage("operator", "conversation", "hello", 6)),
            port.sent,
        )
    }

    @Test
    fun `sendSystemMessage suppresses delivery failures`() {
        val port = RecordingGroupSystemMessagePort(fail = true)
        val service = GroupNotificationService(port)

        service.sendSystemMessage("operator", "conversation", "hello")

        assertEquals(1, port.sent.size)
    }

    private class RecordingGroupSystemMessagePort(
        private val fail: Boolean = false,
    ) : GroupSystemMessagePort {
        val sent = mutableListOf<SentGroupSystemMessage>()

        override fun sendGroupSystemMessage(
            senderId: String,
            conversationId: String,
            content: String,
            contentType: Int,
        ) {
            sent += SentGroupSystemMessage(senderId, conversationId, content, contentType)
            if (fail) error("delivery failed")
        }
    }

    private data class SentGroupSystemMessage(
        val senderId: String,
        val conversationId: String,
        val content: String,
        val contentType: Int,
    )
}
