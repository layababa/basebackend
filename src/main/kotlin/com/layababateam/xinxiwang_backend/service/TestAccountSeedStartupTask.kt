package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TestAccountSeedStartupTask(
    private val testAccountSeedPort: TestAccountSeedPort,
) {
    private val log = LoggerFactory.getLogger(TestAccountSeedStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun seedTestAccounts() {
        try {
            testAccountSeedPort.seedTestAccounts(DefaultTestAccounts.accounts)
        } catch (e: Exception) {
            log.error("seed test accounts error: {}", e.message, e)
        }
    }
}
