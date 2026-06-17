package com.layababateam.xinxiwang_backend.service

/**
 * 后台监控配置管理端口。
 *
 * SDK 复用 HTTP 契约和参数校验，配置存储、缓存失效与后端 Sentry 重载由接入方实现。
 */
interface AdminMonitoringPort {
    fun getValue(key: String): String?
    fun getBooleanValue(key: String, defaultValue: Boolean): Boolean
    fun saveConfigValues(updates: Map<String, String>, descriptions: Map<String, String>)
    fun reloadBackendMonitoring()
}
