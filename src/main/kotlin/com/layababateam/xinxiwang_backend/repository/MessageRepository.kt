package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Message
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository : MongoRepository<Message, String> {
    // 增量同步：获取某个 seqId 之后的消息（带分页限制）
    fun findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
        conversationId: String, seqId: Long, pageable: Pageable
    ): List<Message>

    // 分页拉取历史：获取 seqId 之前的消息（倒序）
    fun findByConversationIdAndSeqIdLessThanOrderBySeqIdDesc(
        conversationId: String, seqId: Long, pageable: Pageable
    ): List<Message>

    // 获取最新 N 条消息
    fun findByConversationIdOrderBySeqIdDesc(
        conversationId: String, pageable: Pageable
    ): List<Message>

    // 按会话 + seqId 精确查找单条消息（补偿重放用）
    fun findByConversationIdAndSeqId(conversationId: String, seqId: Long): Message?

    // 按时间范围统计会话消息总数（使用 idx_conv_created 索引）
    fun countByConversationIdAndCreatedAtGreaterThan(
        conversationId: String, createdAt: Long
    ): Long

    // 按时间范围统计某用户发的消息数（使用 idx_conv_sender_created 索引）
    fun countByConversationIdAndSenderIdAndCreatedAtGreaterThan(
        conversationId: String, senderId: String, createdAt: Long
    ): Long

    // v3: 统计会话中非指定用户的消息总数
    fun countByConversationIdAndSenderIdNotAndCreatedAtGreaterThan(
        conversationId: String, senderId: String, createdAt: Long
    ): Long

    // Admin: 按时间倒序分页查询聊天记录（举报详情用）
    fun findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(
        conversationId: String, createdAt: Long, pageable: Pageable
    ): Page<Message>
}
