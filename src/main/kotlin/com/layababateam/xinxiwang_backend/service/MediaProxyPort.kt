package com.layababateam.xinxiwang_backend.service

/**
 * 历史媒体代理 URL 的解析能力。
 *
 * SDK 负责兼容路由与重定向响应，token 校验、OSS key 解析和 endpoint 选择由接入方实现。
 */
interface MediaProxyPort {
    fun verifyMediaToken(mediaId: String, token: String): Boolean

    fun resolveMediaOssKey(mediaId: String, thumb: Boolean): String?

    fun resolveCompatVideoOssKey(fileName: String, wantsThumbnail: Boolean): String?

    fun currentOssPublicEndpoint(): String
}
