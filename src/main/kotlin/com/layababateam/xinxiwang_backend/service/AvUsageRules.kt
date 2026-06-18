package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AvWeightedUsageDto
import com.layababateam.xinxiwang_backend.dto.TrtcWeightRuleDto
import kotlin.math.ceil

/**
 * 音视频用量纯规则。
 *
 * 这里只维护 TRTC 调试估算所需的分档、权重和计费分钟计算；
 * 业务侧负责读取通话/会议记录并决定统计窗口。
 */
object AvUsageRules {
    const val TIER_AUDIO = "AUDIO"
    const val TIER_SD = "SD"
    const val TIER_HD = "HD"
    const val TIER_FHD = "FHD"
    const val TIER_2K = "2K"
    const val TIER_4K = "4K"

    const val MEDIA_AUDIO = "audio"
    const val MEDIA_VIDEO = "video"

    const val STREAM_TYPE_BIG = "big"
    const val STREAM_TYPE_SMALL = "small"
    const val STREAM_TYPE_SUB = "sub"
    const val STREAM_TYPE_CAMERA = "camera"
    const val STREAM_TYPE_SCREEN = "screen"

    val trtcWeightRules = listOf(
        TrtcWeightRuleDto(TIER_AUDIO, "语音", 1, "纯音频用量"),
        TrtcWeightRuleDto(TIER_SD, "标清视频", 2, "未记录分辨率时按标清估算；有分辨率时按实际段统计"),
        TrtcWeightRuleDto(TIER_HD, "高清视频", 4, "按实际分辨率段统计：长边 >=1280 且短边 >=720"),
        TrtcWeightRuleDto(TIER_FHD, "超清视频", 9, "按实际分辨率段统计：长边 >=1920 且短边 >=1080"),
        TrtcWeightRuleDto(TIER_2K, "2K 视频", 16, "按实际分辨率段统计：长边 >=2560 且短边 >=1440"),
        TrtcWeightRuleDto(TIER_4K, "4K 视频", 36, "按实际分辨率段统计：长边 >=3840 且短边 >=2160"),
    )

    fun normalizeStreamType(streamType: String?): String = when (streamType?.lowercase()) {
        STREAM_TYPE_SUB, STREAM_TYPE_SCREEN -> STREAM_TYPE_SCREEN
        STREAM_TYPE_BIG, STREAM_TYPE_SMALL, STREAM_TYPE_CAMERA -> STREAM_TYPE_CAMERA
        else -> STREAM_TYPE_CAMERA
    }

    fun tierForResolution(width: Int, height: Int): String {
        val (longSide, shortSide) = if (width >= height) width to height else height to width
        return when {
            longSide >= 3840 && shortSide >= 2160 -> TIER_4K
            longSide >= 2560 && shortSide >= 1440 -> TIER_2K
            longSide >= 1920 && shortSide >= 1080 -> TIER_FHD
            longSide >= 1280 && shortSide >= 720 -> TIER_HD
            else -> TIER_SD
        }
    }

    fun tierWeight(tier: String): Int =
        trtcWeightRules.firstOrNull { it.tier == tier }?.weight ?: 1

    fun tierLabel(tier: String): String =
        trtcWeightRules.firstOrNull { it.tier == tier }?.label ?: tier

    fun billableMinutes(seconds: Long): Long =
        if (seconds <= 0) 0 else ceil(seconds / 60.0).toLong()

    fun weightedUsage(seconds: Long, tier: String): AvWeightedUsageDto {
        val weight = tierWeight(tier)
        val rawMinutes = billableMinutes(seconds)
        return AvWeightedUsageDto(
            rawSeconds = seconds,
            weightedSeconds = seconds * weight,
            rawMinutes = rawMinutes,
            weightedMinutes = rawMinutes * weight,
            weight = weight,
            tier = tier,
            label = tierLabel(tier),
        )
    }

    fun buildTierBreakdown(videoTierSeconds: Map<String, Long>): List<AvWeightedUsageDto> =
        videoTierSeconds
            .filterValues { it > 0 }
            .toSortedMap(compareByDescending<String> { tierWeight(it) }.thenBy { it })
            .map { (tier, seconds) -> weightedUsage(seconds, tier) }

    fun combinedVideoUsage(tierSeconds: Map<String, Long>): AvWeightedUsageDto {
        val usages = tierSeconds
            .filterValues { it > 0 }
            .map { (tier, seconds) -> weightedUsage(seconds, tier) }
        if (usages.isEmpty()) return weightedUsage(0, TIER_SD)

        val dominantUsage = usages.maxBy { it.weight }
        return AvWeightedUsageDto(
            rawSeconds = usages.sumOf { it.rawSeconds },
            weightedSeconds = usages.sumOf { it.weightedSeconds },
            rawMinutes = usages.sumOf { it.rawMinutes },
            weightedMinutes = usages.sumOf { it.weightedMinutes },
            weight = dominantUsage.weight,
            tier = dominantUsage.tier,
            label = "视频合计",
        )
    }

    fun callUserMultiplier(callerId: String?, calleeId: String?): Int {
        val userIds = listOfNotNull(callerId, calleeId)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return userIds.size.coerceAtLeast(2)
    }
}
