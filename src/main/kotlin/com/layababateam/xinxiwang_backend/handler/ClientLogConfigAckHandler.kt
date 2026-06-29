package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.service.UserLogConfigService
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class ClientLogConfigAckHandler(
    private val userLogConfigService: UserLogConfigService,
    private val channelDeviceResolver: ChannelDeviceResolver,
) : MessageHandler {
    override val type: String = "client_log_config_ack"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val revision = (data["revision"] as? Number)?.toLong() ?: return
        val enabled = data["criticalLogEnabled"] as? Boolean ?: return
        val deviceId = (data["deviceId"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?: channelDeviceResolver.getDeviceId(ctx.channel())
        userLogConfigService.ack(userId, deviceId, revision, enabled)
    }
}
