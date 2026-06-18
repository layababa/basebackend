package com.layababateam.xinxiwang_backend.config

import com.aliyun.oss.ClientBuilderConfiguration
import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OssConfig(
    @Value("\${aliyun.oss.endpoint-internal}") private val endpointInternal: String,
    @Value("\${aliyun.oss.access-key-id}") private val accessKeyId: String,
    @Value("\${aliyun.oss.access-key-secret}") private val accessKeySecret: String,
) {
    @Bean(name = ["ossClientInternal"], destroyMethod = "shutdown")
    fun ossClientInternal(): OSS {
        val cfg = ClientBuilderConfiguration().apply {
            connectionTimeout = 10_000
            socketTimeout = 60_000
            maxConnections = 200
        }
        return OSSClientBuilder().build(endpointInternal, accessKeyId, accessKeySecret, cfg)
    }

    @Bean(name = ["ossClientPublic"], destroyMethod = "shutdown")
    fun ossClientPublic(): OSS {
        val cfg = ClientBuilderConfiguration().apply {
            connectionTimeout = 10_000
            socketTimeout = 30_000
        }
        return OSSClientBuilder().build(endpointInternal, accessKeyId, accessKeySecret, cfg)
    }
}
