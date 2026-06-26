package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException

object AdminNodeOssEndpointRules {
    fun normalizeOptionalRootUrl(value: String?, label: String): String? {
        val normalized = normalizeRootUrlOrNull(value) ?: return null
        if (!isHttpUrl(normalized)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label 必须以 http:// 或 https:// 开头")
        }
        if (normalized.contains("/config/cdn.json", ignoreCase = true)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "$label 请填写根地址，不带 config/cdn.json")
        }
        return normalized
    }

    fun normalizeRootUrlOrNull(value: String?): String? {
        val trimmed = value?.trim()?.trimEnd('/') ?: return null
        return trimmed.takeIf { it.isNotBlank() }
    }

    fun isHttpUrl(value: String): Boolean {
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    }
}
