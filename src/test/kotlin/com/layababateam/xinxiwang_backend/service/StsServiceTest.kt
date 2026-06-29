package com.layababateam.xinxiwang_backend.service

import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse
import software.amazon.awssdk.services.sts.model.Credentials

class StsServiceTest {
    @Test
    fun `getUploadCredentials rejects blank role arn`() {
        val service = StsService(
            stsClient = recordingStsClient(),
            roleArn = "",
            bucketName = "bucket",
            region = "ap-east-1",
            bucketDomain = "https://bucket.example.com",
        )

        assertEquals(false, service.isConfigured())
        assertFailsWith<IllegalArgumentException> {
            service.getUploadCredentials("images")
        }
    }

    @Test
    fun `getUploadCredentials assumes role with category scoped policy`() {
        var capturedRequest: AssumeRoleRequest? = null
        val service = StsService(
            stsClient = recordingStsClient { request -> capturedRequest = request },
            roleArn = "arn:aws:iam::1:role/upload",
            bucketName = "bucket",
            region = "ap-east-1",
            bucketDomain = "https://bucket.example.com",
        )

        val credentials = service.getUploadCredentials("avatars")

        assertEquals(true, service.isConfigured())
        assertEquals("ak", credentials.accessKeyId)
        assertEquals("sk", credentials.secretAccessKey)
        assertEquals("token", credentials.sessionToken)
        assertEquals("bucket", credentials.bucket)
        assertEquals("ap-east-1", credentials.region)
        assertEquals("https://bucket.example.com", credentials.endpoint)
        assertEquals("arn:aws:iam::1:role/upload", capturedRequest?.roleArn())
        assertTrue(capturedRequest?.policy()?.contains("bucket/avatars/*") == true)
    }

    private fun recordingStsClient(
        onAssumeRole: (AssumeRoleRequest) -> Unit = {},
    ): StsClient = Proxy.newProxyInstance(
        StsClient::class.java.classLoader,
        arrayOf(StsClient::class.java),
    ) { _, method, args ->
        when (method.name) {
            "assumeRole" -> {
                val request = args?.first() as AssumeRoleRequest
                onAssumeRole(request)
                AssumeRoleResponse.builder()
                    .credentials(
                        Credentials.builder()
                            .accessKeyId("ak")
                            .secretAccessKey("sk")
                            .sessionToken("token")
                            .expiration(Instant.parse("2026-06-29T00:00:00Z"))
                            .build()
                    )
                    .build()
            }
            "serviceName" -> "sts"
            "close" -> null
            "toString" -> "RecordingStsClient"
            else -> error("Unsupported StsClient method: ${method.name}")
        }
    } as StsClient
}
