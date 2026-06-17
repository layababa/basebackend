package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "frontend_error_reports")
data class FrontendErrorReport(
    @Id
    val id: String? = null,
    @Indexed
    val eventType: String = "REGISTER_ERROR",
    @Indexed
    val status: String = "PENDING",
    val screen: String? = null,
    val endpoint: String? = null,
    val method: String? = null,
    @Indexed
    val errorCategory: String? = null,
    @Indexed
    val severity: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val statusCode: Int? = null,
    val requestJson: Map<String, Any?> = emptyMap(),
    val registrationInfo: Map<String, Any?> = emptyMap(),
    val clientInfo: Map<String, Any?> = emptyMap(),
    val debugContext: Map<String, Any?> = emptyMap(),
    val remoteAddr: String? = null,
    val userAgent: String? = null,
    val adminNote: String = "",
    val handledBy: String? = null,
    val handledAt: Long? = null,
    @Indexed
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
