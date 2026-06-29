package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.BackendSentryManager
import com.layababateam.xinxiwang_backend.netty.NettyWebSocketHandler
import com.layababateam.xinxiwang_backend.service.push.GroupPushAggregatorService
import com.layababateam.xinxiwang_backend.service.push.PushDaProxyService
import com.layababateam.xinxiwang_backend.service.push.PushDispatchService
import kotlin.test.Test
import kotlin.test.assertEquals

class RemainingRentmsgCoreCompatibilityTest {
    @Test
    fun `remaining rentmsg backend core services are provided by sdk`() {
        val sdkClasses = listOf(
            BackendSentryManager::class.java.simpleName,
            NettyWebSocketHandler::class.java.simpleName,
            AckRetryService::class.java.simpleName,
            AdminAuthService::class.java.simpleName,
            AdminGroupService::class.java.simpleName,
            AdminMessageService::class.java.simpleName,
            AdminPublicGroupService::class.java.simpleName,
            ApnsPushService::class.java.simpleName,
            BanExpiryService::class.java.simpleName,
            CallSessionManager::class.java.simpleName,
            ConversationService::class.java.simpleName,
            FriendService::class.java.simpleName,
            GroupDispatchRecoveryService::class.java.simpleName,
            GroupJoinRequestService::class.java.simpleName,
            GroupService::class.java.simpleName,
            GroupSettingsService::class.java.simpleName,
            GroupUserPreferenceService::class.java.simpleName,
            MeetingService::class.java.simpleName,
            MessageBatchService::class.java.simpleName,
            MessageService::class.java.simpleName,
            MomentService::class.java.simpleName,
            OfficialNotificationService::class.java.simpleName,
            PayNotificationService::class.java.simpleName,
            GroupPushAggregatorService::class.java.simpleName,
            PushDaProxyService::class.java.simpleName,
            PushDispatchService::class.java.simpleName,
            ReadPointFlushService::class.java.simpleName,
            ReadSyncService::class.java.simpleName,
            SystemUserService::class.java.simpleName,
            TrtcService::class.java.simpleName,
            UserSessionManager::class.java.simpleName,
            WalletService::class.java.simpleName,
        )

        assertEquals(
            listOf(
                "BackendSentryManager",
                "NettyWebSocketHandler",
                "AckRetryService",
                "AdminAuthService",
                "AdminGroupService",
                "AdminMessageService",
                "AdminPublicGroupService",
                "ApnsPushService",
                "BanExpiryService",
                "CallSessionManager",
                "ConversationService",
                "FriendService",
                "GroupDispatchRecoveryService",
                "GroupJoinRequestService",
                "GroupService",
                "GroupSettingsService",
                "GroupUserPreferenceService",
                "MeetingService",
                "MessageBatchService",
                "MessageService",
                "MomentService",
                "OfficialNotificationService",
                "PayNotificationService",
                "GroupPushAggregatorService",
                "PushDaProxyService",
                "PushDispatchService",
                "ReadPointFlushService",
                "ReadSyncService",
                "SystemUserService",
                "TrtcService",
                "UserSessionManager",
                "WalletService",
            ),
            sdkClasses,
        )
    }
}
