package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "server_nodes")
data class ServerNode(
    @Id val id: String? = null,
    val name: String,
    val appServerUrl: String,
    val websocketUrl: String,
    val baseUrl: String,
    @Indexed val region: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * 该节点对外暴露的 OSS 加速 / CNAME 域名（不带尾随 /）。
     * 例如中国节点用 "https://oss-cn.example.com"，国际节点用 "https://oss.nooning.cn"。
     * 当前节点上传/拼接 cipherUrl 时使用此字段；为 null 时回退到 application.properties
     * 的 aliyun.oss.endpoint-public 默认值。
     */
    val ossPublicEndpoint: String? = null,
)
