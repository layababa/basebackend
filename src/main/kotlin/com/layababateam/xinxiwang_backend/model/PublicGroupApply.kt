package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "public_group_applies")
data class PublicGroupApply(
    @Id val id: String? = null,
    @Indexed val groupId: String,
    val groupName: String,
    val applicantId: String,
    val applicantName: String,
    val reason: String? = null,
    @Indexed val status: Int = 0,        // 0=待审核, 1=通过, 2=拒绝
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
