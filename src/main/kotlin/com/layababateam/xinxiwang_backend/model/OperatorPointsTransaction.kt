package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 运营积分池流水。每次 debit/credit 同步写一条，便于 admin 后台审计。
 *
 * 不参与业务流程（不影响 OperatorPointsAccount 的乐观锁），失败时仅 log，
 * 不阻塞主路径——审计是次要可靠性。
 */
@Document(collection = "operator_points_transactions")
data class OperatorPointsTransaction(
    @Id
    val id: String? = null,
    /** 'credit' 充值 / 'debit' 扣减 / 'refund' 退回。 */
    @Indexed
    val type: String,
    /** 正数。debit 表示扣多少、credit/refund 表示加多少。 */
    val amount: Long,
    /** 操作后的余额——便于 admin 审计直接核对。 */
    val balanceAfter: Long,
    /** 业务原因：red_packet_create / red_packet_expire_refund / lucky_bag_create
     *  / lucky_bag_unwon_refund / admin_credit 等。 */
    val reason: String,
    /** 触发用户（admin 充值时是充值人，业务扣减时是发起人；群签到积分=签到用户）。 */
    @Indexed
    val operatorId: String? = null,
    /** 关联业务 id / 幂等键（红包 id / 福袋 id / 宣讲 id / 群签到幂等键等）。
     *  群签到积分发放前按 refId 判重以保证幂等（非唯一索引，旧记录 refId 可为 null）。 */
    @Indexed
    val refId: String? = null,
    @Indexed
    val createdAt: Long = System.currentTimeMillis(),
)
