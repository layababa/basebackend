package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestAccountSeedTest {
    @Test
    fun defaultTestAccountsUseFixedNamesPasswordAndOperatorFlag() {
        val accounts = DefaultTestAccounts.accounts

        assertEquals(listOf("000001", "000002", "000003", "000004", "000005"), accounts.map { it.username })
        assertTrue(accounts.all { it.password == "1q2w3e4r5t" })
        assertEquals(listOf("000001"), accounts.filter { it.isOperator }.map { it.username })
        assertTrue(accounts.drop(1).all { !it.isOperator })
    }

    @Test
    fun startupTaskDelegatesToSeedPort() {
        val port = RecordingTestAccountSeedPort()
        val task = TestAccountSeedStartupTask(port)

        task.seedTestAccounts()

        assertEquals(1, port.calls)
    }

    @Test
    fun startupTaskDoesNotBlockApplicationReadyWhenSeedFails() {
        val task = TestAccountSeedStartupTask(
            object : TestAccountSeedPort {
                override fun seedTestAccounts(accounts: List<TestAccountSeedSpec>) {
                    error("seed failed")
                }
            },
        )

        task.seedTestAccounts()
    }

    private class RecordingTestAccountSeedPort : TestAccountSeedPort {
        var calls = 0

        override fun seedTestAccounts(accounts: List<TestAccountSeedSpec>) {
            calls++
            assertEquals(DefaultTestAccounts.accounts, accounts)
        }
    }
}
