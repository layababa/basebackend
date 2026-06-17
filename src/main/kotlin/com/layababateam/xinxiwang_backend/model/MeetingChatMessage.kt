package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "meeting_chat_messages")
@CompoundIndex(def = "{'meetingId': 1, 'createdAt': 1}")
data class MeetingChatMessage(
    @Id val id: String? = null,
    @Indexed val meetingId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
