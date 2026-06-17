package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.index.Indexed

@Document(collection = "meetings")
data class Meeting(
    @Id val id: String? = null,
    val title: String,
    @Indexed(unique = true) val meetingCode: String,
    val creatorId: String,
    val roomId: Int,
    val type: Int = 0,
    val status: Int = 0,
    val participants: List<String> = emptyList(),
    val allParticipants: List<String> = emptyList(),
    val password: String? = null,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val lastEmptyAt: Long? = null
)
