package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.VisibilityType

/**
 * 动态可见性纯规则。
 *
 * 业务侧负责好友关系、黑名单和数据库查询；这里仅处理发布者选择的
 * 可见范围与“最近 N 天”时间窗。
 */
object MomentVisibilityRules {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun isVisibleToUser(
        ownerUserId: String,
        viewerId: String,
        visibilityType: VisibilityType,
        visibilityList: Collection<String>,
    ): Boolean {
        if (ownerUserId == viewerId) return true
        return when (visibilityType) {
            VisibilityType.PUBLIC -> true
            VisibilityType.PRIVATE -> false
            VisibilityType.PARTIAL_VISIBLE -> visibilityList.contains(viewerId)
            VisibilityType.INVISIBLE_TO -> !visibilityList.contains(viewerId)
        }
    }

    fun isVisibleByTime(
        visibility: String,
        createdAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        return when (visibility) {
            "none" -> false
            "3days" -> createdAt > now - 3L * DAY_MS
            "7days" -> createdAt > now - 7L * DAY_MS
            "30days" -> createdAt > now - 30L * DAY_MS
            else -> true
        }
    }

    /**
     * 返回 null 表示不过滤时间；返回 Long.MAX_VALUE 表示全部不可见。
     */
    fun visibilityCutoff(
        visibility: String,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        return when (visibility) {
            "none" -> Long.MAX_VALUE
            "3days" -> now - 3L * DAY_MS
            "7days" -> now - 7L * DAY_MS
            "30days" -> now - 30L * DAY_MS
            else -> null
        }
    }
}
