package com.layababateam.xinxiwang_backend.codec

import tools.jackson.databind.json.JsonMapper
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import com.layababateam.xinxiwang_backend.netty.ProtocolType
import com.layababateam.xinxiwang_backend.proto.ChatMessage
import com.layababateam.xinxiwang_backend.proto.NewMessage
import com.layababateam.xinxiwang_backend.proto.ReplyTo
import com.layababateam.xinxiwang_backend.proto.WsEnvelope
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WsCodecService(
    private val objectMapper: JsonMapper
) {
    private val log = LoggerFactory.getLogger(WsCodecService::class.java)

    private val jsonPrinter: JsonFormat.Printer = JsonFormat.printer()
        .omittingInsignificantWhitespace()

    // ────────────────────────────────────────────
    // 1. Inbound: WsEnvelope -> Map<String, Any?>
    // ────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun envelopeToMap(envelope: WsEnvelope): Map<String, Any?> {
        val type = envelope.type

        return when (envelope.payloadCase) {
            WsEnvelope.PayloadCase.CHAT_MESSAGE ->
                chatMessageToMap(type, envelope.chatMessage)

            WsEnvelope.PayloadCase.NEW_MESSAGE ->
                newMessageToMap(type, envelope.newMessage)

            WsEnvelope.PayloadCase.JSON_FALLBACK -> {
                val parsed = objectMapper.readValue(
                    envelope.jsonFallback, Map::class.java
                ) as Map<String, Any?>
                parsed + ("type" to type)
            }

            WsEnvelope.PayloadCase.PAYLOAD_NOT_SET ->
                mapOf("type" to type)

            else -> protoPayloadToMap(type, envelope)
        }
    }

    // ────────────────────────────────────────────
    // 2. Outbound: Map -> WsEnvelope
    // ────────────────────────────────────────────

    fun mapToEnvelope(type: String, data: Map<String, Any?>): WsEnvelope {
        val builder = WsEnvelope.newBuilder().setType(type)

        when (type) {
            "chat_message" -> builder.setChatMessage(buildChatMessage(data))
            "new_message" -> builder.setNewMessage(buildNewMessage(data))
            else -> builder.setJsonFallback(objectMapper.writeValueAsString(data))
        }

        return builder.build()
    }

    // ────────────────────────────────────────────
    // 3. Frame creation
    // ────────────────────────────────────────────

    fun createFrame(protocol: ProtocolType, data: Map<String, Any?>): WebSocketFrame {
        return when (protocol) {
            ProtocolType.JSON ->
                TextWebSocketFrame(objectMapper.writeValueAsString(data))

            ProtocolType.PROTOBUF -> {
                val type = data["type"] as? String ?: ""
                val envelope = mapToEnvelope(type, data)
                BinaryWebSocketFrame(Unpooled.wrappedBuffer(envelope.toByteArray()))
            }
        }
    }

    // ════════════════════════════════════════════
    // Private: hand-mapped decoders (inbound)
    // ════════════════════════════════════════════

    private fun chatMessageToMap(type: String, msg: ChatMessage): Map<String, Any?> =
        buildMap {
            put("type", type)
            put("conversationId", msg.conversationId)
            put("content", msg.content)
            put("contentType", msg.contentType)
            put("mentions", msg.mentionsList)
            put("clientMessageId", msg.clientMessageId)
            put("replyToMessageId", msg.replyToMessageId)
        }

    private fun newMessageToMap(type: String, msg: NewMessage): Map<String, Any?> =
        buildMap {
            put("type", type)
            put("id", msg.id)
            put("conversationId", msg.conversationId)
            put("senderId", msg.senderId)
            put("senderName", msg.senderName)
            put("senderAvatar", msg.senderAvatar)
            put("contentType", msg.contentType)
            put("content", msg.content)
            put("seqId", msg.seqId)
            put("isRecalled", msg.isRecalled)
            put("mentions", msg.mentionsList)
            put("createdAt", msg.createdAt)
            put("clientMessageId", msg.clientMessageId)
            if (msg.hasReplyTo()) {
                val reply = msg.replyTo
                put("replyTo", mapOf(
                    "messageId" to reply.messageId,
                    "senderId" to reply.senderId,
                    "senderName" to reply.senderName,
                    "content" to reply.content,
                    "contentType" to reply.contentType
                ))
            }
            put("senderRole", msg.senderRole)
            put("groupName", msg.groupName)
            put("groupAvatar", msg.groupAvatar)
        }

    // ════════════════════════════════════════════
    // Private: hand-mapped builders (outbound)
    // ════════════════════════════════════════════

    @Suppress("UNCHECKED_CAST")
    private fun buildChatMessage(data: Map<String, Any?>): ChatMessage {
        return ChatMessage.newBuilder().apply {
            (data["conversationId"] as? String)?.let { conversationId = it }
            (data["content"] as? String)?.let { content = it }
            (data["contentType"] as? Number)?.let { contentType = it.toInt() }
            (data["mentions"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.forEach { addMentions(it) }
            (data["clientMessageId"] as? String)?.let { clientMessageId = it }
            (data["replyToMessageId"] as? String)?.let { replyToMessageId = it }
        }.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildNewMessage(data: Map<String, Any?>): NewMessage {
        return NewMessage.newBuilder().apply {
            (data["id"] as? String)?.let { id = it }
            (data["conversationId"] as? String)?.let { conversationId = it }
            (data["senderId"] as? String)?.let { senderId = it }
            (data["senderName"] as? String)?.let { senderName = it }
            (data["senderAvatar"] as? String)?.let { senderAvatar = it }
            (data["contentType"] as? Number)?.let { contentType = it.toInt() }
            (data["content"] as? String)?.let { content = it }
            (data["seqId"] as? Number)?.let { seqId = it.toLong() }
            (data["isRecalled"] as? Boolean)?.let { isRecalled = it }
            (data["mentions"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.forEach { addMentions(it) }
            (data["createdAt"] as? Number)?.let { createdAt = it.toLong() }
            (data["clientMessageId"] as? String)?.let { clientMessageId = it }
            (data["replyTo"] as? Map<String, Any?>)?.let { reply ->
                replyTo = ReplyTo.newBuilder().apply {
                    (reply["messageId"] as? String)?.let { messageId = it }
                    (reply["senderId"] as? String)?.let { senderId = it }
                    (reply["senderName"] as? String)?.let { senderName = it }
                    (reply["content"] as? String)?.let { content = it }
                    (reply["contentType"] as? Number)?.let { contentType = it.toInt() }
                }.build()
            }
            (data["senderRole"] as? Number)?.let { senderRole = it.toInt() }
            (data["groupName"] as? String)?.let { groupName = it }
            (data["groupAvatar"] as? String)?.let { groupAvatar = it }
        }.build()
    }

    // ════════════════════════════════════════════
    // Private: generic proto -> Map (via JsonFormat + reflection)
    // ════════════════════════════════════════════

    @Suppress("UNCHECKED_CAST")
    private fun protoPayloadToMap(type: String, envelope: WsEnvelope): Map<String, Any?> {
        return try {
            val payloadField = envelope.descriptorForType
                .findFieldByNumber(envelope.payloadCase.number)
            if (payloadField != null) {
                val message = envelope.getField(payloadField) as? Message
                if (message != null) {
                    val json = jsonPrinter.print(message)
                    val parsed = objectMapper.readValue(
                        json, Map::class.java
                    ) as Map<String, Any?>
                    return parsed + ("type" to type)
                }
            }
            mapOf("type" to type)
        } catch (e: Exception) {
            log.warn("Failed to decode protobuf payload for type '{}': {}", type, e.message)
            mapOf("type" to type)
        }
    }
}
