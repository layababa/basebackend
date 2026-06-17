package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Bot

data class AdminBotCreateResult(
    val bot: Bot,
    val apiKey: String,
)

interface AdminBotManagementPort {
    fun createManagedBot(
        adminId: String,
        username: String,
        displayName: String,
        avatarUrl: String,
        description: String,
    ): AdminBotCreateResult

    fun updateManagedBot(
        botId: String,
        displayName: String?,
        avatarUrl: String?,
        description: String?,
    ): Bot

    fun regenerateManagedBotApiKey(botId: String): String

    fun updateManagedBotStatus(botId: String, status: Int): Bot

    fun deleteManagedBot(botId: String)
}
