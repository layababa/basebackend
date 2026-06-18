package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.GroupCheckin
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupCheckinRepository : MongoRepository<GroupCheckin, String> {
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: String): List<GroupCheckin>

    /** 活动唯一性校验：同群同一时间仅 1 个进行中(status=0)活动。 */
    fun findFirstByConversationIdAndStatus(conversationId: String, status: Int): GroupCheckin?

    /** admin 列表：按标题关键字 + 状态分页。 */
    fun findByTitleContainingAndStatusOrderByCreatedAtDesc(
        title: String,
        status: Int,
        pageable: Pageable,
    ): Page<GroupCheckin>

    fun findByTitleContainingOrderByCreatedAtDesc(title: String, pageable: Pageable): Page<GroupCheckin>

    fun findByStatusOrderByCreatedAtDesc(status: Int, pageable: Pageable): Page<GroupCheckin>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<GroupCheckin>
}
