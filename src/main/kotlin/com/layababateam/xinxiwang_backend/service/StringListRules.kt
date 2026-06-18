package com.layababateam.xinxiwang_backend.service

/**
 * 字符串列表解析纯规则。
 *
 * 适用于请求参数、配置项和批量 ID 等通用场景；业务含义由调用方决定。
 */
object StringListRules {
    fun delimited(
        value: String?,
        delimiters: CharArray = charArrayOf(','),
        max: Int = Int.MAX_VALUE,
        distinct: Boolean = true,
    ): List<String> {
        val values = value?.split(*delimiters).orEmpty()
        return nonBlank(values, max, distinct)
    }

    fun nonBlank(
        values: Iterable<String?>,
        max: Int = Int.MAX_VALUE,
        distinct: Boolean = true,
    ): List<String> {
        val boundedMax = max.coerceAtLeast(0)
        val sequence = values.asSequence()
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        val normalized = if (distinct) sequence.distinct() else sequence
        return normalized.take(boundedMax).toList()
    }
}
