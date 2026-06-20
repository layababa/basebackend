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
            // 短连接超时 + 砍重试：OSS 对外网络/DNS 抖动会反复发生（2026-06-20 断线事件），
            // 默认 connectionTimeout=10s × maxErrorRetry=3 会让一次 objectExists(HEAD) 卡死 ~40s，
            // 同步跑在 ws-biz 线程上 → 线程池饥饿 → WS 假死。这里在 SDK 层中心化收紧到最坏 ~6s。
            // socketTimeout 保持 60s：该 client 还用于大媒体流读(getObjectStream/Range)，不能砍。
            connectionTimeout = 3_000
            socketTimeout = 60_000
            maxConnections = 200
            maxErrorRetry = 1
        }
        return OSSClientBuilder().build(endpointInternal, accessKeyId, accessKeySecret, cfg)
    }

    @Bean(name = ["ossClientPublic"], destroyMethod = "shutdown")
    fun ossClientPublic(): OSS {
        val cfg = ClientBuilderConfiguration().apply {
            // 见 ossClientInternal 注释。public client 仅用于本地签名(generatePresignedUrl)，
            // 不走网络，但同样收紧以保持一致、并兜底任何意外的网络调用。
            connectionTimeout = 3_000
            socketTimeout = 30_000
            maxErrorRetry = 1
        }
        return OSSClientBuilder().build(endpointInternal, accessKeyId, accessKeySecret, cfg)
    }
}
