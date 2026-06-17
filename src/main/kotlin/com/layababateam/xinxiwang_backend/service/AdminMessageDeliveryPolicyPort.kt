package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicy

/**
 * 后台消息扩散策略管理端口。
 *
 * SDK 复用后台 HTTP 契约、参数校验和审计动作，策略存储、缓存失效与命中规则由接入方实现。
 */
interface AdminMessageDeliveryPolicyPort {
    fun listPolicies(): List<MessageDeliveryPolicy>
    fun findPolicy(id: String): MessageDeliveryPolicy?
    fun savePolicy(policy: MessageDeliveryPolicy): MessageDeliveryPolicy
    fun deletePolicy(id: String)
    fun policyExists(id: String): Boolean
}
