package com.layababateam.xinxiwang_backend.service

/**
 * 后台群消息 Signal Pull 灰度配置端口。
 *
 * SDK 复用 HTTP 契约、默认值和参数归一化，配置读写与缓存失效由接入方实现。
 */
interface AdminGroupMessageSignalPort {
    fun getConfigValues(keys: List<String>): Map<String, String>
    fun saveConfigValues(values: Map<String, String>, descriptions: Map<String, String>)
}
