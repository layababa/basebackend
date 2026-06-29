package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.model.AppLatestVersion
import com.layababateam.xinxiwang_backend.repository.AppLatestVersionRepository
import com.layababateam.xinxiwang_backend.service.ApkDownloadUrlResolver
import com.layababateam.xinxiwang_backend.service.ClientUpdatePolicyService
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
    private val apkDownloadUrlResolver: ApkDownloadUrlResolver,
    private val clientUpdatePolicyService: ClientUpdatePolicyService,
    @Value("\${app.ci-secret:changeme}") private val ciSecret: String
) {
    data class VersionPushRequest(
        val platform: String,
        val version: String,
        val buildNumber: Int,
        val downloadUrl: String,
        val releaseNotes: String? = null
    )

    @PostMapping("/push")
    fun pushVersion(
        @RequestHeader("X-CI-Secret") secret: String,
        @RequestBody request: VersionPushRequest
    ): ResponseEntity<ApiResponse<Any>> {
        if (secret != ciSecret) {
            return ResponseEntity.status(403).body(ApiResponse.error("Invalid CI secret"))
        }

        val platform = ClientVersionRules.normalizePlatform(request.platform)
            ?: return ResponseEntity.badRequest().body(
                ApiResponse.error("平台必须是: ${ClientVersionRules.supportedPlatforms.joinToString(", ")}")
            )

        val existing = appLatestVersionRepository.findByPlatform(platform)
        val downloadUrl = apkDownloadUrlResolver.resolve(request.downloadUrl)
        val record = if (existing != null) {
            existing.copy(
                latestVersion = request.version,
                buildNumber = request.buildNumber,
                downloadUrl = downloadUrl,
                releaseNotes = request.releaseNotes ?: existing.releaseNotes,
                updatedAt = System.currentTimeMillis(),
                updatedBy = "ci"
            )
        } else {
            AppLatestVersion(
                platform = platform,
                latestVersion = request.version,
                buildNumber = request.buildNumber,
                downloadUrl = downloadUrl,
                releaseNotes = request.releaseNotes,
                updatedBy = "ci"
            )
        }

        val saved = appLatestVersionRepository.save(record)
        return ResponseEntity.ok(ApiResponse.ok(saved))
    }

    @GetMapping("/check")
    fun checkVersion(
        @RequestParam platform: String,
        @RequestParam version: String
    ): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.ok(ApiResponse.ok(clientUpdatePolicyService.checkVersion(platform, version).toResponseMap()))
}
