package com.layababateam.xinxiwang_backend.service

/**
 * 配置值边界纯规则。
 *
 * 适用于从配置中心、环境变量或数据库读取数值后做统一边界归一化；
 * 业务侧负责定义配置项含义和默认值。
 */
object ConfigBoundaryRules {
    fun intValue(value: Int?, default: Int, min: Int, max: Int = Int.MAX_VALUE): Int =
        (value ?: default).coerceIn(min, max)

    fun longValue(value: Long?, default: Long, min: Long, max: Long = Long.MAX_VALUE): Long =
        (value ?: default).coerceIn(min, max)
}
