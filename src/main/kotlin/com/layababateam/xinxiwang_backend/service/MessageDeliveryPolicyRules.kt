package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.MessageDeliveryMode
import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicy
import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicyScope

data class MessageDeliveryDecision(
    val mode: MessageDeliveryMode,
    val reason: String,
    val policyId: String? = null,
)

/**
 * 消息投递策略纯规则。
 *
 * 接入方负责策略持久化与缓存，SDK 统一维护匹配顺序、默认值和版本范围判断。
 */
object MessageDeliveryPolicyRules {
    fun decide(
        policies: List<MessageDeliveryPolicy>,
        userId: String?,
        platform: String?,
        clientVersion: String?,
    ): MessageDeliveryDecision {
        val normalizedUserId = userId?.takeIf { it.isNotBlank() }
        val normalizedPlatform = platform?.takeIf { it.isNotBlank() }
        val normalizedVersion = clientVersion?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        if (normalizedPlatform == null || normalizedVersion == null) {
            return fullPushDefault("policy_full_push_missing_context")
        }

        val orderedPolicies = sortPolicies(policies)
        val direct = orderedPolicies.firstOrNull {
            normalizedUserId != null && it.scope == MessageDeliveryPolicyScope.USER && it.userId == normalizedUserId
        }
        if (direct != null) return decisionFromPolicy(direct, "policy_user")

        val batch = orderedPolicies.firstOrNull {
            normalizedUserId != null && it.scope == MessageDeliveryPolicyScope.USER_BATCH && normalizedUserId in it.userIds
        }
        if (batch != null) return decisionFromPolicy(batch, "policy_user_batch")

        val version = orderedPolicies.firstOrNull {
            it.scope == MessageDeliveryPolicyScope.PLATFORM_VERSION && matchesPlatformVersion(
                policy = it,
                platform = normalizedPlatform,
                clientVersion = normalizedVersion,
            )
        }
        if (version != null) return decisionFromPolicy(version, "policy_platform_version")

        return fullPushDefault("policy_full_push_default")
    }

    fun sortPolicies(policies: List<MessageDeliveryPolicy>): List<MessageDeliveryPolicy> =
        policies.sortedWith(policyOrdering)

    fun matchesPlatformVersion(
        policy: MessageDeliveryPolicy,
        platform: String,
        clientVersion: String,
    ): Boolean {
        val policyPlatform = policy.platform?.takeIf { it.isNotBlank() }
        if (policyPlatform != null && !policyPlatform.equals(platform, ignoreCase = true)) return false
        return ClientVersionRules.versionInRange(clientVersion, policy.minClientVersion, policy.maxClientVersion)
    }

    fun scopeWeight(scope: MessageDeliveryPolicyScope): Int = when (scope) {
        MessageDeliveryPolicyScope.USER -> 300
        MessageDeliveryPolicyScope.USER_BATCH -> 200
        MessageDeliveryPolicyScope.PLATFORM_VERSION -> 100
    }

    private fun decisionFromPolicy(policy: MessageDeliveryPolicy, reason: String): MessageDeliveryDecision =
        MessageDeliveryDecision(policy.mode, reason, policy.id)

    private fun fullPushDefault(reason: String): MessageDeliveryDecision =
        MessageDeliveryDecision(MessageDeliveryMode.FULL_PUSH, reason)

    private val policyOrdering = compareByDescending<MessageDeliveryPolicy> { scopeWeight(it.scope) }
        .thenByDescending { it.priority }
        .thenByDescending { it.updatedAt }
}
