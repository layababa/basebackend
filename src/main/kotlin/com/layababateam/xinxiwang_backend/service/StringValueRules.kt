package com.layababateam.xinxiwang_backend.service

/**
 * 单个字符串值解析纯规则。
 *
 * 用于请求字段、配置字段和策略字段的基础清洗；业务侧仍负责判定字段语义。
 */
object StringValueRules {
    fun nonBlank(value: String?, max: Int = Int.MAX_VALUE): String? {
        val boundedMax = max.coerceAtLeast(0)
        return value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(boundedMax)
    }

    fun lowerNonBlank(value: String?, max: Int = Int.MAX_VALUE): String? =
        nonBlank(value, max)?.lowercase()

    fun nonBlankOr(value: String?, default: String, max: Int = Int.MAX_VALUE): String =
        nonBlank(value, max) ?: default
}
