package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "group_join_requests")
@CompoundIndex(def = "{'conversationId': 1, 'status': 1}")
data class GroupJoinRequest(
    @Id
    val id: String? = null,
    val conversationId: String,
    val applicantId: String,
    val inviterId: String? = null,
    val message: String = "",
    val status: Int = 0,           // 0=待审核 1=已同意 2=已拒绝
    val createdAt: Long = System.currentTimeMillis(),
    val handledAt: Long? = null,
    val handledBy: String? = null
)
