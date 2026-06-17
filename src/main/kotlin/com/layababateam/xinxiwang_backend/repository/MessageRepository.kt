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

    fun findFirstByConversationIdOrderBySeqIdDesc(conversationId: String): Message?

    // 按时间范围统计未读数（排除自己发的消息）
    fun countByConversationIdAndSenderIdNotAndCreatedAtGreaterThan(
        conversationId: String, senderId: String, createdAt: Long
    ): Long

    // Admin: 按时间倒序分页查询聊天记录（举报详情用）
    fun findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(
        conversationId: String, createdAt: Long, pageable: Pageable
    ): Page<Message>

    // 按内容类型筛选媒体消息（图片/视频等）
    fun findByConversationIdAndContentTypeInOrderBySeqIdDesc(
        conversationId: String, contentTypes: List<Int>, pageable: Pageable
    ): List<Message>
}
