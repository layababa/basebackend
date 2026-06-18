package com.layababateam.xinxiwang_backend.service

/**
 * 请求元数据纯规则。
 *
 * 业务侧负责从框架请求对象读取 header；SDK 统一维护转发链解析和
 * 常见客户端 IP 兜底策略。
 */
object RequestMetadataRules {
    fun forwardedIps(forwardedFor: String?): List<String> =
        StringListRules.delimited(forwardedFor)

    fun firstForwardedIp(forwardedFor: String?): String? =
        forwardedIps(forwardedFor).firstOrNull()

    fun clientIp(
        forwardedFor: String?,
        realIp: String?,
        remoteAddr: String?,
    ): String? =
        firstForwardedIp(forwardedFor)
            ?: StringValueRules.nonBlank(realIp)
            ?: StringValueRules.nonBlank(remoteAddr)
}
