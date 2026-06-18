package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.model.AppLatestVersion
import com.layababateam.xinxiwang_backend.repository.AppLatestVersionRepository
import com.layababateam.xinxiwang_backend.service.ClientVersionRules
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/open/version")
class AppVersionController(
    private val appLatestVersionRepository: AppLatestVersionRepository,
    @Value("\${app.ci-secret:changeme}") private val ciSecret: String,
) {
    data class VersionPushRequest(
        val platform: String,
        val version: String,
        val buildNumber: Int,
        val downloadUrl: String,
        val releaseNotes: String? = null,
    )

    @PostMapping("/push")
    fun pushVersion(
        @RequestHeader("X-CI-Secret") secret: String,
        @RequestBody request: VersionPushRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        if (secret != ciSecret) {
            return ResponseEntity.status(403).body(ApiResponse.error("Invalid CI secret"))
        }

        if (!ClientVersionRules.isSupportedPlatform(request.platform)) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error("平台必须是: ${ClientVersionRules.supportedPlatforms.joinToString()}")
            )
        }

        val existing = appLatestVersionRepository.findByPlatform(request.platform)
        val record = if (existing != null) {
            existing.copy(
                latestVersion = request.version,
                buildNumber = request.buildNumber,
                downloadUrl = request.downloadUrl,
                releaseNotes = request.releaseNotes ?: existing.releaseNotes,
                updatedAt = System.currentTimeMillis(),
                updatedBy = CI_UPDATED_BY,
            )
        } else {
            AppLatestVersion(
                platform = request.platform,
                latestVersion = request.version,
                buildNumber = request.buildNumber,
                downloadUrl = request.downloadUrl,
                releaseNotes = request.releaseNotes,
                updatedBy = CI_UPDATED_BY,
            )
        }

        val saved = appLatestVersionRepository.save(record)
        return ResponseEntity.ok(ApiResponse.ok(saved))
    }

    @GetMapping("/check")
    fun checkVersion(
        @RequestParam platform: String,
        @RequestParam version: String,
    ): ResponseEntity<ApiResponse<Any>> {
        val latest = appLatestVersionRepository.findByPlatform(platform)
            ?: return ResponseEntity.ok(
                ApiResponse.ok(
                    mapOf(
                        "hasUpdate" to false,
                        "currentVersion" to version,
                    )
                )
            )

        val fullLatest = "${latest.latestVersion}+${latest.buildNumber}"
        val hasUpdate = ClientVersionRules.compareVersions(version, fullLatest) < 0
        val minForceVersion = latest.minForceVersion
        val forceUpdate = if (hasUpdate && latest.forceUpdate && minForceVersion != null) {
            ClientVersionRules.compareVersions(version, minForceVersion) < 0
        } else {
            false
        }

        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "hasUpdate" to hasUpdate,
                    "forceUpdate" to forceUpdate,
                    "latestVersion" to latest.latestVersion,
                    "buildNumber" to latest.buildNumber,
                    "downloadUrl" to latest.downloadUrl,
                    "releaseNotes" to latest.releaseNotes,
                    "currentVersion" to version,
                )
            )
        )
    }

    private companion object {
        const val CI_UPDATED_BY = "ci"
    }
}
