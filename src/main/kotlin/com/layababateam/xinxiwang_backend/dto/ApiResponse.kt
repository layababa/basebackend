package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.dto.ErrorCode

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val code: String = "0"
) {
    companion object {
        fun <T> ok(data: T? = null, message: String = "OK") =
            ApiResponse(success = true, message = message, data = data, code = ErrorCode.SUCCESS.code)

        fun <T> error(errorCode: ErrorCode, message: String? = null) =
            ApiResponse<T>(success = false, message = message ?: errorCode.defaultMessage, code = errorCode.code)

        // 保留旧签名以兼容现有调用
        fun <T> error(message: String) =
            ApiResponse<T>(success = false, message = message, code = ErrorCode.UNKNOWN_ERROR.code)
    }
}

data class PagedData<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int
)
