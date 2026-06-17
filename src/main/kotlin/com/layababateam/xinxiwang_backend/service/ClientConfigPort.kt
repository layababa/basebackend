package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.GroupMessageSignalClientConfigDto
import com.layababateam.xinxiwang_backend.dto.MonitoringConfigDto
import jakarta.servlet.http.HttpServletRequest

/**
 * 客户端启动配置能力。
 *
 * SDK 负责公开配置接口；监控配置来源、灰度策略和 token 解析仍由接入方实现。
 */
interface ClientConfigPort {
    fun getMonitoringConfig(): MonitoringConfigDto

    fun getGroupMessageSignalConfig(request: HttpServletRequest): GroupMessageSignalClientConfigDto
}
