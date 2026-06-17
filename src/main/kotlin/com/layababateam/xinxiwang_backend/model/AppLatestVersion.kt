package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "app_latest_versions")
data class AppLatestVersion(
    @Id val id: String? = null,
    @Indexed(unique = true) val platform: String,
    val latestVersion: String,
    val buildNumber: Int,
    val downloadUrl: String,
    val releaseNotes: String? = null,
    val forceUpdate: Boolean = false,
    val minForceVersion: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "ci"
)
