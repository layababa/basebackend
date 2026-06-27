package com.layababateam.xinxiwang_backend.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.TextIndexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "users")
data class User(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val username: String,       // 账号名称
    @TextIndexed
    val displayName: String,    // 显示名称
    val avatarUrl: String,      // 头像url
    val gender: Int,            // 性别 0: 男, 1: 女, 2: 未知
    val bio: String,            // 自我介绍
    @get:JsonIgnore
    val passwordHash: String = "",   // 密码哈希
    val inviteCode: String,     // 注册时使用的邀请码
    @Indexed(unique = true)
    val myInviteCode: String = "", // 给新用户生成的邀请码
    val invitedBy: String? = null, // 邀请人的 userId
    val version: Long = 1,      // 信息版本号，修改资料时递增
    val updatedAt: Long = System.currentTimeMillis(), // 最后更新时间
    // ── 朋友圈字段 ──
    val momentsBgUrl: String? = null,       // 朋友圈背景图URL
    val momentsVisibility: String = "all",  // "all" | "none" | "3days" | "7days" | "30days"
    // ── 钱包字段 ──
    @Indexed
    val bscAddress: String? = null,         // BSC 充值地址
    val walletBalance: String = "0",        // 积分余额
    val frozenBalance: String = "0",        // 冻结余额（提现审核中）
    @JsonIgnore
    val paymentPasswordHash: String? = null, // 支付密码哈希
    // ── Bot 字段 ──
    val isBot: Boolean = false,
    // ── 运营号 ──
    val isOperator: Boolean = false,
    @Indexed
    val assignedCsUserId: String? = null,
    @Indexed
    val assignedCsQrCodeId: String? = null,
    @Indexed
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deletedReason: String? = null
)
