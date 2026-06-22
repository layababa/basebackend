package com.layababateam.xinxiwang_backend.service

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class VirtualMemberCandidate(
    val id: String,
    val displayName: String,
) {
    companion object {
        fun stableIdFor(displayName: String): String = VirtualMemberCatalog.virtualIdFor(displayName)
    }
}

@Component
class VirtualMemberCatalog private constructor(
    val candidates: List<VirtualMemberCandidate>,
) {
    constructor() : this(loadDisplayNames(DEFAULT_RESOURCE_PATH).toCandidates())

    fun selectUnused(
        count: Int,
        usedVirtualIds: Set<String>,
        usedVirtualDisplayNames: Set<String>,
        realMemberIds: Set<String>,
    ): List<VirtualMemberCandidate> {
        require(count in 0..100) { "虚拟成员数量必须在0-100之间" }

        val available = availableCandidates(
            usedVirtualIds = usedVirtualIds,
            usedVirtualDisplayNames = usedVirtualDisplayNames,
            realMemberIds = realMemberIds,
        )
        if (available.size < count) {
            throw IllegalArgumentException("虚拟成员池不足，最多还能添加 ${available.size} 个")
        }
        return available
            .take(count)
    }

    fun availableCount(
        usedVirtualIds: Set<String>,
        usedVirtualDisplayNames: Set<String>,
        realMemberIds: Set<String>,
    ): Int {
        return availableCandidates(
            usedVirtualIds = usedVirtualIds,
            usedVirtualDisplayNames = usedVirtualDisplayNames,
            realMemberIds = realMemberIds,
        ).size
    }

    private fun availableCandidates(
        usedVirtualIds: Set<String>,
        usedVirtualDisplayNames: Set<String>,
        realMemberIds: Set<String>,
    ): List<VirtualMemberCandidate> {
        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.id !in usedVirtualIds &&
                    candidate.displayName !in usedVirtualDisplayNames &&
                    candidate.id !in realMemberIds &&
                    candidate.displayName !in realMemberIds
            }
            .toList()
    }

    companion object {
        private const val DEFAULT_RESOURCE_PATH = "virtual-members/china_im_ids.md"

        fun fromDisplayNames(displayNames: Iterable<String>): VirtualMemberCatalog =
            VirtualMemberCatalog(parseLines(displayNames).toCandidates())

        fun loadDisplayNames(resourcePath: String = DEFAULT_RESOURCE_PATH): List<String> {
            val resource = ClassPathResource(resourcePath)
            require(resource.exists()) { "虚拟成员ID资源不存在: $resourcePath" }
            return resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parseLines(reader.readLines())
            }
        }

        fun parseLines(lines: Iterable<String>): List<String> {
            return lines
                .map { line -> line.trim() }
                .filter { line -> line.isNotBlank() }
                .map { line -> line.removePrefix("*").trim().replace("\\_", "_") }
                .filter { line -> line.isNotBlank() }
                .distinct()
        }

        fun virtualIdFor(displayName: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(displayName.toByteArray(StandardCharsets.UTF_8))
            val hex = digest.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            return "virtual:${hex.take(16)}"
        }

        private fun List<String>.toCandidates(): List<VirtualMemberCandidate> =
            map { displayName ->
                VirtualMemberCandidate(
                    id = virtualIdFor(displayName),
                    displayName = displayName,
                )
            }
    }
}

typealias VirtualParticipantCandidate = VirtualMemberCandidate
typealias VirtualParticipantCatalog = VirtualMemberCatalog
