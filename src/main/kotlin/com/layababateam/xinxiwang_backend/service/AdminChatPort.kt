package com.layababateam.xinxiwang_backend.service

interface AdminChatPort {
    fun getUserConversations(userId: String): Any

    fun getConversationMessages(conversationId: String, page: Int, size: Int): Map<String, Any?>

    fun getConversationDetail(conversationId: String): Any?

    fun searchMessages(
        keyword: String?,
        userId: String?,
        startDate: Long?,
        endDate: Long?,
        contentType: Int?,
        page: Int,
        size: Int,
    ): Map<String, Any?>

    fun deleteMessage(messageId: String, adminId: String, adminUsername: String, ipAddress: String)

    fun recallMessage(messageId: String, adminId: String, adminUsername: String, ipAddress: String)

    fun broadcast(content: String, contentType: Int, adminId: String, adminUsername: String, ipAddress: String)

    fun multicast(
        userIds: List<String>,
        content: String,
        contentType: Int,
        adminId: String,
        adminUsername: String,
        ipAddress: String,
    )
}
