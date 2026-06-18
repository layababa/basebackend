package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 运营积分池账户。MVP 阶段全局单行（id="global"），后续可按运营组织拆分。
 * 使用 @Version 走乐观锁，调用方在冲突时重试一次。
 */
@Document(collection = "operator_points_accounts")
data class OperatorPointsAccount(
    @Id
    val id: String = "global",
    val balance: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    @Version
    val version: Long = 0
)
