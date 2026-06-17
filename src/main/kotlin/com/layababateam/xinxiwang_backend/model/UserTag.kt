package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_tags_meta")
data class UserTag(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val normalizedName: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Document(collection = "user_tag_bindings")
@CompoundIndex(def = "{'userId': 1, 'tagId': 1}", unique = true)
data class UserTagBinding(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,
    @Indexed
    val tagId: String,
    val assignedBy: String,
    val createdAt: Long = System.currentTimeMillis()
)
