package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * CDN 配置启动发布壳。
 *
 * SDK 负责启动触发；业务侧 [CdnConfigStartupPublishPort] 保留异步发布和目标存储逻辑。
 */
@Component
class CdnConfigStartupPublishTask(
    private val cdnConfigStartupPublishPort: CdnConfigStartupPublishPort,
) {
    private val log = LoggerFactory.getLogger(CdnConfigStartupPublishTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun publishCdnConfigOnStartup() {
        try {
            cdnConfigStartupPublishPort.publishCdnConfigOnStartup()
        } catch (e: Exception) {
            log.warn("publish cdn config on startup error: {}", e.message)
        }
    }
}
