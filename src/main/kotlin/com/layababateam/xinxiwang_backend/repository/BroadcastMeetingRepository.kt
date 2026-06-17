package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BroadcastMeeting
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BroadcastMeetingRepository : MongoRepository<BroadcastMeeting, String> {
    fun findByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): Page<BroadcastMeeting>

    @Query("{ 'status': { '\$in': ?0 } }")
    fun findByStatusIn(statuses: List<String>, pageable: Pageable): Page<BroadcastMeeting>

    fun findByArchivedFalseAndStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): Page<BroadcastMeeting>

    fun countByStatus(status: String): Long

    @Query("{ 'status': 'scheduled', 'scheduledAt': { '\$lte': ?0 } }")
    fun findScheduledDue(now: Long): List<BroadcastMeeting>

    /**
     * 推送 15 分钟提醒用：scheduled / waiting 状态下、scheduledAt 在 (now, now+15min]
     * 范围内的宣讲。调度器扫描后逐一调 BroadcastNotificationService.dispatchReminder。
     * notifiedReminder 防重由订阅记录侧管理，repo 这里不过滤——可能多次扫到但
     * 第二次起 pending 集合是空，dispatchReminder 内部会兜底。
     */
    @Query(
        "{ 'status': { '\$in': ['scheduled', 'waiting'] }, " +
            "'scheduledAt': { '\$gt': ?0, '\$lte': ?1 } }",
    )
    fun findUpcomingForReminder(now: Long, end: Long): List<BroadcastMeeting>

    @Query("{ 'status': 'ended', 'endedAt': { '\$lt': ?0 }, 'archived': false }")
    fun findEndedBefore(threshold: Long): List<BroadcastMeeting>

    /// 创建宣讲前检查"一人一场 live"约束用：找出某用户当前所有未结束的宣讲。
    /// 包含 live / waiting / scheduled，因为这些状态都"占位"该用户作为主讲人。
    fun findBySpeakerIdAndStatusIn(speakerId: String, statuses: List<String>): List<BroadcastMeeting>
}
