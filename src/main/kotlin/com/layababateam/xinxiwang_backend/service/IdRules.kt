package com.layababateam.xinxiwang_backend.service

import java.util.UUID

/**
 * ID 生成纯规则。
 *
 * 统一短追踪 ID 和随机锁值等无需持久序列的标识生成。
 */
object IdRules {
    fun uuid(): String =
        UUID.randomUUID().toString()

    fun shortUuid(length: Int = DEFAULT_SHORT_LENGTH): String =
        uuid().take(length.coerceAtLeast(0))

    private const val DEFAULT_SHORT_LENGTH = 8
}
