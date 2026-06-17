package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.PullLogReportPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class PullLogAckHandler(
    private val pullLogReportPort: PullLogReportPort,
) : MessageHandler {
    override val type: String = "pull_log_ack"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String ?: return
        pullLogReportPort.acknowledgePullLog(userId, requestId)
    }
}

@Component
class PullLogDoneHandler(
    private val pullLogReportPort: PullLogReportPort,
) : MessageHandler {
    override val type: String = "pull_log_done"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String ?: return
        pullLogReportPort.completePullLog(
            userId = userId,
            requestId = requestId,
            logObjectKey = data["logObjectKey"] as? String,
            fileSize = (data["fileSize"] as? Number)?.toLong(),
            fileCount = (data["fileCount"] as? Number)?.toInt(),
        )
    }
}

@Component
class PullLogFailedHandler(
    private val pullLogReportPort: PullLogReportPort,
) : MessageHandler {
    override val type: String = "pull_log_failed"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val requestId = data["requestId"] as? String ?: return
        pullLogReportPort.failPullLog(
            userId = userId,
            requestId = requestId,
            errorCode = data["errorCode"] as? String,
            errorMsg = data["errorMsg"] as? String,
        )
    }
}
