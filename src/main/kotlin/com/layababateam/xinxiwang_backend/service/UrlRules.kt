package com.layababateam.xinxiwang_backend.service

/**
 * URL 字符串规范化纯规则。
 *
 * 只处理格式边界，不做业务域名或协议白名单判断。
 */
object UrlRules {
    fun stripTrailingSlash(value: String): String =
        value.trimEnd('/')

    fun stripTrailingSlashOrNull(value: String?): String? =
        value?.trimEnd('/')
}
