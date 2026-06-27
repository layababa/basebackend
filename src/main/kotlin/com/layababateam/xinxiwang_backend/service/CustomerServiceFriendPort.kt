package com.layababateam.xinxiwang_backend.service

interface CustomerServiceFriendPort {
    fun ensureCustomerServiceFriendship(userId: String, customerServiceUserId: String)
}
