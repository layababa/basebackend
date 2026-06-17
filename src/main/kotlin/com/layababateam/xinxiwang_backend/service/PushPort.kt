package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApnsTokenRequest
import com.layababateam.xinxiwang_backend.dto.PushDaBindingStatusDto

/**
 * 客户端推送配置与 token 管理能力。
 *
 * SDK 负责公开 API 契约；设备会话存储、PushDa 绑定和外部代理调用由接入方实现。
 */
interface PushPort {
    fun registerApnsToken(userId: String, authToken: String, request: ApnsTokenRequest): Boolean

    fun clearApnsToken(userId: String, authToken: String)

    fun clearVoipToken(userId: String)

    fun clearApnsOnly(userId: String, authToken: String)

    fun getPushDaBindingStatus(userId: String): PushDaBindingStatusDto

    fun getPushDaStoreLinks(): Map<String, String>

    fun reportPushDaActive(bindingUid: String)
}
