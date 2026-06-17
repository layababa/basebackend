package com.layababateam.xinxiwang_backend.service

interface PullLogReportPort {
    fun acknowledgePullLog(userId: String, requestId: String)

    fun completePullLog(
        userId: String,
        requestId: String,
        logObjectKey: String?,
        fileSize: Long?,
        fileCount: Int?,
    )

    fun failPullLog(
        userId: String,
        requestId: String,
        errorCode: String?,
        errorMsg: String?,
    )
}
