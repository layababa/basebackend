package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "pushda_bindings")
data class PushDaBinding(
    @Id val id: String? = null,
    @Indexed val userId: String,
    @Indexed(unique = true) val bindingUid: String,
    val createdAt: Long = System.currentTimeMillis()
)
