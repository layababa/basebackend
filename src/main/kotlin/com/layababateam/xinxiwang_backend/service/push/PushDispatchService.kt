package com.layababateam.xinxiwang_backend.service.push

import com.layababateam.xinxiwang_backend.service.ApnsPushService
import org.springframework.stereotype.Service

/**
 * 推送分發服務
 *
 * 作為 UserSessionManager 與 ApnsPushService 之間的中間層，
 * 從根本上消除兩者之間的循環依賴。
 * 支援 APNs（iOS）和 PushDa 代理推送（Android 國產手機）。
 */
@Service
class PushDispatchService(
    private val apnsPushService: ApnsPushService,
    private val pushDaProxyService: PushDaProxyService
) {

    fun pushToOfflineDevices(userId: String, wsMessage: String, onlineAuthTokens: Set<String>) {
        apnsPushService.pushToOfflineDevices(userId, wsMessage, onlineAuthTokens)
        pushDaProxyService.pushIfBound(userId, wsMessage)
    }

    fun pushVoipToOfflineDevices(userId: String, callData: Map<String, Any>, onlineAuthTokens: Set<String>) {
        apnsPushService.pushVoipToOfflineDevices(userId, callData, onlineAuthTokens)
    }

    /**
     * Plan A：對所有離線 iOS 設備發送來電 alert push（取代 VoIP push）。
     */
    fun pushIncomingCallAlertToOfflineDevices(
        userId: String,
        callData: Map<String, Any>,
        onlineAuthTokens: Set<String>
    ) {
        apnsPushService.pushIncomingCallAlertToOfflineDevices(userId, callData, onlineAuthTokens)
    }

    /**
     * 推送聚合后的群消息通知（APNs + PushDa）。
     * 推送内容为 "你收到了 X 条新消息"，使用 collapse-id 避免堆叠。
     */
    fun pushAggregatedGroupNotification(
        userId: String,
        title: String,
        body: String,
        customData: Map<String, Any>,
        badgeCount: Int,
        convId: String,
        onlineAuthTokens: Set<String> = emptySet()
    ) {
        apnsPushService.sendAggregatedGroupPush(userId, title, body, customData, badgeCount, convId, onlineAuthTokens)
        pushDaProxyService.pushAggregatedGroup(userId, title, body, convId)
    }
}
