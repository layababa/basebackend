package com.layababateam.xinxiwang_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Deprecated("Replaced by OssService. Delete after OSS stability confirmed.")
@Configuration
@ConditionalOnProperty(
    name = ["xinxiwang.legacy.s3.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class S3Config(
    @Value("\${media.region:ap-southeast-1}") private val region: String,
) {
    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.of(region))
        .build()

    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder()
        .region(Region.of(region))
        .build()
}
