package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicy

data class MediaDecryptConfigResponse(
    val backendDecryptMasterEnabled: Boolean,
    val defaultBackendDecryptEnabled: Boolean,
    val policies: List<MediaDecryptPolicy>,
)

data class MediaDecryptMasterRequest(
    val backendDecryptMasterEnabled: Boolean? = null,
)

data class MediaDecryptDefaultRequest(
    val defaultBackendDecryptEnabled: Boolean? = null,
)

data class MediaDecryptPolicyRequest(
    val scope: String? = null,
    val enabled: Boolean? = null,
    val backendDecryptEnabled: Boolean? = null,
    val userId: String? = null,
    val platform: String? = null,
    val minClientVersion: String? = null,
    val maxClientVersion: String? = null,
    val priority: Int? = null,
    val note: String? = null,
)
