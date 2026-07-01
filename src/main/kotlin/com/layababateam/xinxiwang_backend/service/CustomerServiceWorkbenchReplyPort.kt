package com.layababateam.xinxiwang_backend.service

data class CustomerServiceWorkbenchReplyCommand(
    val customerServiceUserId: String,
    val customerUserId: String,
    val contentType: Int,
    val content: String,
    val imageUrl: String? = null,
)

data class CustomerServiceWorkbenchReplyResult(
    val messageId: String? = null,
    val conversationId: String? = null,
)

data class CustomerServiceWorkbenchVisitorMessageCommand(
    val customerUserId: String,
    val customerServiceUserId: String,
    val contentType: Int,
    val content: String,
    val imageUrl: String? = null,
)

interface CustomerServiceWorkbenchReplyPort {
    fun sendCustomerServiceReply(command: CustomerServiceWorkbenchReplyCommand): CustomerServiceWorkbenchReplyResult

    fun sendCustomerVisitorMessage(command: CustomerServiceWorkbenchVisitorMessageCommand): CustomerServiceWorkbenchReplyResult =
        CustomerServiceWorkbenchReplyResult()
}
