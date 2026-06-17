package com.layababateam.xinxiwang_backend.service

/**
 * 连接限流旁路策略。
 *
 * SDK 负责基础连接限流，业务侧可按环境策略决定是否跳过限流检查。
 */
interface ConnectionRateLimitBypassPort {
    fun shouldBypassConnectionRateLimit(): Boolean
}
