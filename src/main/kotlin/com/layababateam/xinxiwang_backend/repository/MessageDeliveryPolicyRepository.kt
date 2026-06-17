package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicy
import org.springframework.data.mongodb.repository.MongoRepository

interface MessageDeliveryPolicyRepository : MongoRepository<MessageDeliveryPolicy, String> {
    fun findAllByEnabledTrue(): List<MessageDeliveryPolicy>
    fun findAllByOrderByPriorityDescUpdatedAtDesc(): List<MessageDeliveryPolicy>
}
