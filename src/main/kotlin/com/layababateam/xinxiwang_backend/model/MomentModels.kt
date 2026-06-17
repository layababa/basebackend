package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "moments")
data class Moment(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,
    val content: String,
    val location: Location? = null,
    val visibilityType: VisibilityType = VisibilityType.PUBLIC,
    val visibilityList: List<String> = emptyList(), // 用于 PARTIAL_VISIBLE (只给谁看) 或 INVISIBLE_TO (不给谁看) 的 userId 列表
    @Indexed
    val createdAt: Long = System.currentTimeMillis()
)

data class Location(
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?
)

enum class VisibilityType {
    PUBLIC,             // 公开
    PRIVATE,            // 仅自己可见
    PARTIAL_VISIBLE,    // 部分可见（选中的人）
    INVISIBLE_TO        // 不给谁看（排除的人）
}

@Document(collection = "moment_medias")
data class MomentMedia(
    @Id
    val id: String? = null,
    @Indexed
    val momentId: String,
    val url: String,
    val type: MediaType, // IMAGE or VIDEO
    val sortOrder: Int,
    val thumbnailUrl: String? = null // 视频封面图 URL
)

enum class MediaType {
    IMAGE, VIDEO
}

@Document(collection = "moment_comments")
data class MomentComment(
    @Id
    val id: String? = null,
    @Indexed
    val momentId: String,
    @Indexed
    val userId: String,
    val replyToUserId: String? = null,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Document(collection = "moment_likes")
@CompoundIndex(def = "{'momentId': 1, 'userId': 1}", unique = true)
data class MomentLike(
    @Id
    val id: String? = null,
    val momentId: String,
    val userId: String,
    val createdAt: Long = System.currentTimeMillis()
)
