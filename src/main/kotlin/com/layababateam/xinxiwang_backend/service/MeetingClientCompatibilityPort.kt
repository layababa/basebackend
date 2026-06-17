package com.layababateam.xinxiwang_backend.service

import jakarta.servlet.http.HttpServletRequest

/**
 * 会议接口的客户端兼容策略入口。
 *
 * SDK 只关心“当前请求是否支持预约会议协议”，具体兼容号、灰度开关和日志由业务侧实现。
 */
interface MeetingClientCompatibilityPort {
    val meetingScheduleUpdateMessage: String

    fun supportsMeetingSchedule(request: HttpServletRequest): Boolean
}
