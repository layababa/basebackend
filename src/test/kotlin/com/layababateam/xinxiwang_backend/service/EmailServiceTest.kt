package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test

class EmailServiceTest {
    @Test
    fun `send returns without error when DirectMail is not configured`() {
        val service = EmailService(
            accessKeyId = "",
            accessKeySecret = "",
            accountName = "noreply@example.com",
            fromAlias = "Rentmsg",
            regionId = "cn-hangzhou",
        )

        service.send("user@example.com", "subject", "body")
    }
}
