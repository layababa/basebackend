package com.layababateam.xinxiwang_backend.service

/**
 * 宣讲运营积分池能力。
 *
 * SDK 复用接口契约，运营身份校验和积分账户实现由接入方处理。
 */
interface OperatorBalancePort {
    fun getBalance(operatorUserId: String): Long

    fun credit(operatorUserId: String, amount: Long, reason: String?): Long
}
