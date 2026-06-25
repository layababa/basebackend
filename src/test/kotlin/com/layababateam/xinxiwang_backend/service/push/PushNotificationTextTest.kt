package com.layababateam.xinxiwang_backend.service.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushNotificationTextTest {
    @Test
    fun `new group message text includes group title sender body and deep link`() {
        val text = PushNotificationText.from(
            type = "new_message",
            data = mapOf(
                "senderName" to "Alice",
                "groupName" to "研发群",
                "contentType" to 0,
                "content" to "hello",
                "conversationId" to "c1",
            ),
            appScheme = "xinxiwang",
        )

        assertEquals("研发群", text?.title)
        assertEquals("Alice: hello", text?.body)
        assertEquals("xinxiwang://chat/c1", text?.deepLink)
        assertEquals("c1", text?.customData?.get("conversationId"))
    }

    @Test
    fun `unknown type falls back to title and body fields`() {
        val text = PushNotificationText.from("custom", mapOf("title" to "提醒", "body" to "内容"))

        assertEquals("提醒", text?.title)
        assertEquals("内容", text?.body)
        assertEquals("custom", text?.customData?.get("type"))
    }

    @Test
    fun `null type returns null`() {
        assertNull(PushNotificationText.from(null, emptyMap<String, Any>()))
    }
}
