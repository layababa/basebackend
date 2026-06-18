package com.layababateam.xinxiwang_backend.service

import java.util.Base64

/**
 * 字节编码纯规则。
 *
 * 只统一编码格式；调用方仍负责选择载荷、加密和签名策略。
 */
object EncodingRules {
    fun base64(value: ByteArray): String =
        Base64.getEncoder().encodeToString(value)

    fun base64UrlNoPadding(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    fun decodeBase64(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    fun decodeBase64Url(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)

    fun trtcBase64(value: ByteArray): String =
        base64(value)
            .replace("+", "*")
            .replace("/", "-")
            .replace("=", "_")
}
