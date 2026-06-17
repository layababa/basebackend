package com.layababateam.xinxiwang_backend.service

import jakarta.servlet.http.HttpServletRequest

/**
 * 登录蜜罐命中记录入口。
 *
 * SDK 负责暴露诱捕路由和解析请求字段，具体风控上下文、指标和封禁策略由接入方实现。
 */
interface LoginHoneypotPort {
    fun recordHoneypot(
        username: String,
        request: HttpServletRequest,
        deviceId: String?,
        deviceName: String?,
        platform: String?,
        clientVersion: String?,
    )
}
