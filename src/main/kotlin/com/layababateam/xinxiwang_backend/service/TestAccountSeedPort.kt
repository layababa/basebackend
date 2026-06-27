package com.layababateam.xinxiwang_backend.service

data class TestAccountSeedSpec(
    val username: String,
    val password: String,
    val isOperator: Boolean,
)

object DefaultTestAccounts {
    const val PASSWORD = "1q2w3e4r5t"

    val accounts: List<TestAccountSeedSpec> = listOf(
        TestAccountSeedSpec(username = "000001", password = PASSWORD, isOperator = true),
        TestAccountSeedSpec(username = "000002", password = PASSWORD, isOperator = false),
        TestAccountSeedSpec(username = "000003", password = PASSWORD, isOperator = false),
        TestAccountSeedSpec(username = "000004", password = PASSWORD, isOperator = false),
        TestAccountSeedSpec(username = "000005", password = PASSWORD, isOperator = false),
    )
}

interface TestAccountSeedPort {
    fun seedTestAccounts(accounts: List<TestAccountSeedSpec>)
}
