package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

data class CustomField(
    val label: String,
    val value: String
)

@Document(collection = "business_cards")
data class BusinessCard(
    @Id
    val id: String? = null,
    val userId: String,
    val name: String,
    val title: String? = null,
    val company: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val avatarUrl: String? = null,
    val website: String? = null,
    val customFields: List<CustomField> = emptyList(),
    val isDefault: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
