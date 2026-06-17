package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.CallStateLookupPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import io.netty.channel.ChannelHandlerContext

/**
 * App resume 后同步服务端通话状态，避免客户端本地 callState 残留。
 */
@Component
class GetMyCallStateHandler(
    private val callStateLookupPort: CallStateLookupPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    private val log = LoggerFactory.getLogger(GetMyCallStateHandler::class.java)
    override val type = "get_my_call_state"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val state = callStateLookupPort.getCallState(userId)
        val payload = mapOf(
            "inCall" to state.inCall,
            "roomId" to state.roomId,
            "peerId" to state.peerId,
            "answered" to state.answered,
        )
        log.debug(
            "[GET-MY-CALL-STATE] userId={} inCall={} roomId={}",
            userId,
            state.inCall,
            state.roomId,
        )
        wsResponseSender.send(ctx, mapOf("type" to "my_call_state", "data" to payload))
    }
}
