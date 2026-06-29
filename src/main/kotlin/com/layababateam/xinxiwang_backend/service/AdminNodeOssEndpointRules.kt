package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException

object AdminNodeOssEndpointRules {
    fun normalizeOptionalRootUrl(value: String?, label: String): String? {
        val normalized = normalizeRootUrlOrNull(value) ?: return null
        if (!isHttpUrl(normalized)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label must start with http:// or https://")
        }
        if (normalized.contains("/config/cdn.json", ignoreCase = true)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label must be the OSS root, without config/cdn.json")
        }
        if (!isStandardAliyunOssBucketRoot(normalized)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label must be a standard Aliyun OSS bucket host")
        }
        return normalized
    }

    fun normalizeRootUrlOrNull(value: String?): String? {
        val trimmed = value?.trim()?.trimEnd('/') ?: return null
        return trimmed.takeIf { it.isNotBlank() }
    }

    fun normalizeOptionalCredential(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() }

    fun validateEndpointCredentialPair(
        endpoint: String?,
        accessKeyId: String?,
        accessKeySecret: String?,
        label: String,
    ) {
        val hasId = !accessKeyId.isNullOrBlank()
        val hasSecret = !accessKeySecret.isNullOrBlank()
        if (hasId != hasSecret) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label AccessKeyId and AccessKeySecret must be provided together")
        }
        if ((hasId || hasSecret) && endpoint == null) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label AccessKey requires an OSS endpoint")
        }
    }

    fun isHttpUrl(value: String): Boolean {
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    }

    fun isStandardAliyunOssBucketRoot(value: String): Boolean {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.path.isNotBlank() && uri.path != "/") return false
        return OSS_BUCKET_HOST_REGEX.matches(host)
    }

    private val OSS_BUCKET_HOST_REGEX =
        Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]\\.oss-[a-z0-9-]+\\.aliyuncs\\.com$")
}
