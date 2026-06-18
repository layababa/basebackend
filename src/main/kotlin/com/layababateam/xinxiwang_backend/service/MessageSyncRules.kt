package com.layababateam.xinxiwang_backend.service

/**
 * 消息同步纯规则。
 *
 * 这里只处理 cursor / seq 的边界归一化；业务侧负责权限、可见性和拉取策略。
 */
object MessageSyncRules {
    fun fromSeqId(fromSeqId: Long): Long =
        fromSeqId.coerceAtLeast(0)
}
