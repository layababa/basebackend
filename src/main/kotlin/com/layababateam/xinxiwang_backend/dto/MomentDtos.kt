package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.model.Location
import com.layababateam.xinxiwang_backend.model.MediaType
import com.layababateam.xinxiwang_backend.model.VisibilityType
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PublishMomentRequest(
    @field:Size(max = 2000, message = "動態內容不可超過 2000 個字元")
    val content: String = "",

    val location: Location? = null,

    val visibilityType: VisibilityType = VisibilityType.PUBLIC,

    @field:Size(max = 100, message = "可見清單不可超過 100 人")
    val visibilityList: List<String> = emptyList(),

    @field:Size(max = 9, message = "媒體數量不可超過 9 個")
    @field:Valid
    val medias: List<MediaInfo> = emptyList()
)

data class MediaInfo(
    @field:NotBlank(message = "媒體連結不可為空白")
    @field:Size(max = 500, message = "媒體連結不可超過 500 個字元")
    val url: String,

    val type: MediaType,

    @field:Min(value = 0, message = "排序索引最小為 0")
    @field:Max(value = 8, message = "排序索引最大為 8")
    val sortOrder: Int,

    @field:Size(max = 500, message = "縮圖連結不可超過 500 個字元")
    val thumbnailUrl: String? = null // 视频封面图 URL
)

data class AddCommentRequest(
    @field:NotBlank(message = "動態識別碼不可為空白")
    val momentId: String,

    @field:NotBlank(message = "評論內容不可為空白")
    @field:Size(max = 500, message = "評論內容不可超過 500 個字元")
    val content: String,

    val replyToUserId: String? = null
)

data class MomentDto(
    val id: String,
    val userId: String,
    val userAvatarUrl: String,
    val userDisplayName: String,
    val content: String,
    val location: Location?,
    val visibilityType: VisibilityType,
    val createdAt: Long,
    val medias: List<MediaInfo>,
    val likes: List<LikeDto>,
    val comments: List<CommentDto>
)

data class LikeDto(
    val id: String,
    val userId: String,
    val userDisplayName: String,
    val createdAt: Long
)

data class CommentDto(
    val id: String,
    val userId: String,
    val userDisplayName: String,
    val replyToUserId: String?,
    val replyToUserDisplayName: String?,
    val content: String,
    val createdAt: Long
)

data class TimelineResponse(
    val moments: List<MomentDto>,
    val hasMore: Boolean
)
