package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Meeting
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MeetingRepository : MongoRepository<Meeting, String> {
    fun findByMeetingCode(code: String): Meeting?
    fun findByRoomId(roomId: Int): Meeting?
    fun findByIdAndStatus(id: String, status: Int): Meeting?

    @Query("{ '\$or': [ { 'participants': ?0 }, { 'creatorId': ?0 } ] }")
    fun findUserMeetings(userId: String, pageable: Pageable): Page<Meeting>

    /** 查询指定用户参与的所有进行中会议（供断线清理使用） */
    @Query("{ 'status': 0, 'participants': ?0 }")
    fun findActiveMeetingsByParticipant(userId: String): List<Meeting>

    /** 查询所有进行中且 lastEmptyAt 不为空的会议（参与者已清空） */
    @Query("{ 'status': 0, 'lastEmptyAt': { '\$ne': null } }")
    fun findActiveEmptyMeetings(): List<Meeting>

    /** 查询所有进行中且参与者列表为空的会议（含 lastEmptyAt 未设置的异常数据） */
    @Query("{ 'status': 0, 'participants': { '\$size': 0 } }")
    fun findActiveWithEmptyParticipants(): List<Meeting>

    /** 查询所有进行中且创建时间早于指定时间的会议（超时清理） */
    @Query("{ 'status': 0, 'createdAt': { '\$lt': ?0 } }")
    fun findActiveMeetingsCreatedBefore(createdBefore: Long): List<Meeting>

    /** 管理后台：所有会议分页查询 */
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Meeting>

    /** 管理后台：按状态查询会议 */
    fun findByStatusOrderByCreatedAtDesc(status: Int, pageable: Pageable): Page<Meeting>

    /** 管理后台：按标题关键字搜索 */
    @Query("{ 'title': { '\$regex': ?0, '\$options': 'i' } }")
    fun findByTitleRegex(titleRegex: String, pageable: Pageable): Page<Meeting>

    /** 管理后台：按标题关键字 + 状态搜索 */
    @Query("{ 'title': { '\$regex': ?0, '\$options': 'i' }, 'status': ?1 }")
    fun findByTitleRegexAndStatus(titleRegex: String, status: Int, pageable: Pageable): Page<Meeting>

    /** 查询指定用户参与过的、在指定时间之后结束的会议（供重连同步使用） */
    @Query("{ 'status': 1, 'allParticipants': ?0, 'endedAt': { '\$gte': ?1 } }")
    fun findEndedMeetingsByParticipant(userId: String, endedAfter: Long): List<Meeting>
}
