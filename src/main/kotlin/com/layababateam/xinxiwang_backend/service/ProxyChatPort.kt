package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.MessageDto

/**
 * AI 代聊 SDK 端口。
 *
 * basebackend 只负责统一 HTTP 契约、鉴权入口和参数校验；具体存储、消息路由、IM 发送由业务仓实现。
 */
interface ProxyChatPort {
    /** 管理端开启代聊：一个被代聊用户一次最多绑定 50 个沟通用户。 */
    fun startProxyChat(userId: String, targetIds: List<String>, botUserId: String, metadata: Map<String, String>)

    /** 管理端停止代聊：建议业务实现软关闭，方便恢复和审计。 */
    fun stopProxyChat(userId: String, targetIds: List<String>)

    /** 管理端查询某个被代聊用户的代聊配置。 */
    fun queryProxyChats(userId: String, page: Int, size: Int): ProxyChatPage

    /** BotAI 查询分配给自己的有效代聊任务。 */
    fun listProxyChatsForBot(botUserId: String): List<ProxyChatDto>

    /** BotAI 拉取指定代聊私聊会话的历史/增量消息。 */
    fun getProxyChatMessages(
        botUserId: String,
        userId: String,
        targetId: String,
        afterSeqId: Long?,
        beforeSeqId: Long?,
        limit: Int,
    ): ProxyChatMessagesResult

    /** BotAI 以被代聊用户 userId 的身份向 targetId 发送 IM 消息。 */
    fun sendProxyChatMessage(
        botUserId: String,
        userId: String,
        targetId: String,
        content: String,
        contentType: Int,
    ): BotMessageSendResult
}

data class ProxyChatDto(
    val id: String? = null,
    /** 被代聊用户：BotAI 回复时使用这个身份。 */
    val userId: String,
    /** 沟通用户：私聊另一方。 */
    val targetId: String,
    /** 实际处理代聊的 Bot 用户。 */
    val botUserId: String,
    /** 业务透传变量：人设、场景、标签等。 */
    val metadata: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

data class ProxyChatPage(
    val proxyChats: List<ProxyChatDto>,
    val total: Long,
    val hasNext: Boolean,
)

data class ProxyChatMessagesResult(
    val conversationId: String,
    val messages: List<MessageDto>,
)

/**
 * 业务仓在 IM 消息入口可复用的代聊事件格式。
 *
 * 命中代聊时，建议向 botUserId 推送：{"type":"proxychat_new_message","data": ProxyChatIncomingMessageEvent}
 */
data class ProxyChatIncomingMessageEvent(
    val userId: String,
    val targetId: String,
    val botUserId: String,
    val conversationId: String,
    val messageId: String?,
    val seqId: Long,
    val contentType: Int,
    val content: String,
    val createdAt: Long,
    val metadata: Map<String, String> = emptyMap(),
)
