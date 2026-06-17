package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_relations_settings")
data class UserRelationSetting(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,
    @Indexed
    val targetUserId: String,
    val hideMyMoments: Boolean = false, // 不让他看我的朋友圈
    val hideHisMoments: Boolean = false // 不看他的朋友圈
)
