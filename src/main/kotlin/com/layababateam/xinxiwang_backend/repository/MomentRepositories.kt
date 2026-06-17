package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.*
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MomentRepository : MongoRepository<Moment, String> {
    fun findByUserId(userId: String): List<Moment>
    fun findByUserId(userId: String, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Moment>
    fun findByUserIdAndCreatedAtGreaterThan(userId: String, createdAt: Long, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Moment>
    fun deleteByUserId(userId: String)
    fun findByUserIdIn(userIds: List<String>, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Moment>
    fun findFirstByUserIdInOrderByCreatedAtDesc(userIds: List<String>): Moment?
}

@Repository
interface MomentMediaRepository : MongoRepository<MomentMedia, String> {
    fun findByMomentId(momentId: String): List<MomentMedia>
    fun findByMomentIdIn(momentIds: List<String>): List<MomentMedia>
    fun deleteByMomentId(momentId: String)
}

@Repository
interface MomentCommentRepository : MongoRepository<MomentComment, String> {
    fun findByMomentId(momentId: String): List<MomentComment>
    fun findByMomentIdIn(momentIds: List<String>): List<MomentComment>
    fun deleteByMomentId(momentId: String)
    fun deleteByUserId(userId: String)
}

@Repository
interface MomentLikeRepository : MongoRepository<MomentLike, String> {
    fun findByMomentId(momentId: String): List<MomentLike>
    fun findByMomentIdIn(momentIds: List<String>): List<MomentLike>
    fun findByMomentIdAndUserId(momentId: String, userId: String): MomentLike?
    fun deleteByMomentId(momentId: String)
    fun deleteByUserId(userId: String)
}

@Repository
interface UserRelationSettingRepository : MongoRepository<UserRelationSetting, String> {
    fun findByUserIdAndTargetUserId(userId: String, targetUserId: String): UserRelationSetting?
    fun findByUserIdAndHideHisMomentsTrue(userId: String): List<UserRelationSetting>
    fun findByTargetUserIdAndHideMyMomentsTrue(targetUserId: String): List<UserRelationSetting>
}
