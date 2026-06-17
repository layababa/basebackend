package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.GroupV3SyncRequest
import com.layababateam.xinxiwang_backend.dto.GroupV3SyncResponse

/**
 * 群消息 V3 同步 HTTP 能力契约。
 *
 * SDK 复用路由与响应格式；同步查询、限流、超时、配置和指标由接入方实现。
 */
interface GroupV3SyncPort {
    fun syncGroup(userId: String, request: GroupV3SyncRequest): GroupV3SyncResult
}

data class GroupV3SyncResult(
    val status: Int,
    val data: GroupV3SyncResponse? = null,
    val errorCode: ErrorCode? = null,
    val message: String? = null,
) {
    companion object {
        fun ok(data: GroupV3SyncResponse): GroupV3SyncResult =
            GroupV3SyncResult(status = 200, data = data)

        fun error(status: Int, errorCode: ErrorCode, message: String?): GroupV3SyncResult =
            GroupV3SyncResult(status = status, errorCode = errorCode, message = message)
    }
}
