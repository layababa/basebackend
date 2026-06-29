package com.layababateam.xinxiwang_backend.service

import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest

@Deprecated("Replaced by OssService. Delete after OSS stability confirmed.")
@Service
@ConditionalOnProperty(
    name = ["rentmsg.legacy.s3.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class StsService(
    private val stsClient: StsClient,
    @Value("\${media.sts.role-arn:}") private val roleArn: String,
    @Value("\${media.bucket_general_name}") private val bucketName: String,
    @Value("\${media.region}") private val region: String,
    @Value("\${media.bucket_general_domain}") private val bucketDomain: String,
) {
    private val log = LoggerFactory.getLogger(StsService::class.java)

    companion object {
        private const val SESSION_DURATION_SECONDS = 3600
        private val VALID_CATEGORIES = setOf("avatars", "images", "videos", "audio", "files", "stickers")
    }

    data class UploadCredentials(
        val accessKeyId: String,
        val secretAccessKey: String,
        val sessionToken: String,
        val expiration: String,
        val bucket: String,
        val region: String,
        val endpoint: String,
    )

    fun isConfigured(): Boolean = roleArn.isNotBlank()

    fun getUploadCredentials(category: String): UploadCredentials {
        require(roleArn.isNotBlank()) { "STS role ARN not configured" }

        val dir = if (category in VALID_CATEGORIES) category else "files"
        val policy = """
        {
            "Version": "2012-10-17",
            "Statement": [{
                "Effect": "Allow",
                "Action": [
                    "s3:PutObject",
                    "s3:AbortMultipartUpload",
                    "s3:ListMultipartUploadParts"
                ],
                "Resource": "arn:aws:s3:::$bucketName/$dir/*"
            }]
        }
        """.trimIndent()

        val response = stsClient.assumeRole(
            AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("upload-${UUID.randomUUID()}")
                .durationSeconds(SESSION_DURATION_SECONDS)
                .policy(policy)
                .build(),
        )

        log.info("Issued STS credentials for category=$dir, expires=${response.credentials().expiration()}")

        return UploadCredentials(
            accessKeyId = response.credentials().accessKeyId(),
            secretAccessKey = response.credentials().secretAccessKey(),
            sessionToken = response.credentials().sessionToken(),
            expiration = response.credentials().expiration().toString(),
            bucket = bucketName,
            region = region,
            endpoint = bucketDomain,
        )
    }
}
