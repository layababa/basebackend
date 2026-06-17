package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicy

/**
 * 后台媒体解密策略管理端口。
 *
 * SDK 复用 HTTP 契约、参数校验和审计动作，策略命中规则、全局开关存储和缓存失效由接入方实现。
 */
interface AdminMediaDecryptPort {
    fun globalMasterEnabled(): Boolean
    fun globalDefaultEnabled(): Boolean
    fun listPolicies(): List<MediaDecryptPolicy>
    fun findPolicy(id: String): MediaDecryptPolicy?
    fun savePolicy(policy: MediaDecryptPolicy): MediaDecryptPolicy
    fun deletePolicy(id: String)
    fun saveBooleanConfig(key: String, value: Boolean, description: String): String?
}
