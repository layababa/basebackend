package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminAddBannedWordRequest
import com.layababateam.xinxiwang_backend.dto.AdminBatchAddBannedWordsRequest
import com.layababateam.xinxiwang_backend.dto.AdminBatchDeleteRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.model.BannedWord
import com.layababateam.xinxiwang_backend.model.BannedWordHit
import com.layababateam.xinxiwang_backend.model.SystemConfig
import com.layababateam.xinxiwang_backend.service.AdminSystemPort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import com.layababateam.xinxiwang_backend.service.StringListRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/system")
class AdminSystemController(
    private val adminSystemPort: AdminSystemPort,
    private val auditLogPort: AuditLogPort,
) {
    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping("/config")
    fun getSystemConfig(): ResponseEntity<ApiResponse<List<SystemConfig>>> {
        return ResponseEntity.ok(ApiResponse.ok(adminSystemPort.listSystemConfig()))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/config")
    fun updateSystemConfig(
        request: HttpServletRequest,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "配置内容不能为空"))
        }

        val updated = adminSystemPort.saveSystemConfig(body)
        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "UPDATE_SYSTEM_CONFIG",
            targetType = "SYSTEM_CONFIG",
            targetId = null,
            details = "更新系统配置: ${body.keys.joinToString(", ")}",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(updated, "系统配置更新成功"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping("/asset-switches")
    fun getAssetSwitches(): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        return ResponseEntity.ok(ApiResponse.ok(adminSystemPort.getAssetSwitches()))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/asset-switches")
    fun updateAssetSwitches(
        request: HttpServletRequest,
        @RequestBody body: Map<String, Boolean>,
    ): ResponseEntity<ApiResponse<*>> {
        val redPacketEnabled = body["redPacketEnabled"]
        val transferEnabled = body["transferEnabled"]
        if (redPacketEnabled == null && transferEnabled == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "至少需要提供一个开关配置"))
        }

        val requestedSwitches = linkedMapOf<String, Boolean>()
        redPacketEnabled?.let { requestedSwitches["redPacketEnabled"] = it }
        transferEnabled?.let { requestedSwitches["transferEnabled"] = it }

        val updated = adminSystemPort.saveAssetSwitches(requestedSwitches)
        val details = requestedSwitches.entries.joinToString(", ") { (key, value) ->
            val label = if (key == "redPacketEnabled") "红包" else "转账"
            val status = if (value) "启用" else "禁用"
            "$label: $status"
        }
        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "UPDATE_ASSET_SWITCHES",
            targetType = "SYSTEM_CONFIG",
            targetId = null,
            details = "更新资产开关: $details",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(updated, "资产开关更新成功"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping("/banned-words")
    fun listBannedWords(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<BannedWord>>> {
        return ResponseEntity.ok(ApiResponse.ok(adminSystemPort.listBannedWords(page, size)))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PostMapping("/banned-words")
    fun addBannedWord(
        request: HttpServletRequest,
        @Valid @RequestBody body: AdminAddBannedWordRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.word.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "违禁词不能为空"))
        }
        if (adminSystemPort.bannedWordExists(body.word)) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "该违禁词已存在"))
        }

        val adminId = adminId(request)
        val bannedWord = adminSystemPort.addBannedWord(body.word, adminId)
        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername(request),
            action = "ADD_BANNED_WORD",
            targetType = "BANNED_WORD",
            targetId = bannedWord.id,
            details = "添加违禁词: ${body.word}",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(bannedWord, "违禁词添加成功"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @DeleteMapping("/banned-words/{id}")
    fun deleteBannedWord(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        val bannedWord = adminSystemPort.findBannedWord(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "违禁词不存在"))
        adminSystemPort.deleteBannedWord(id)
        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "DELETE_BANNED_WORD",
            targetType = "BANNED_WORD",
            targetId = id,
            details = "删除违禁词: ${bannedWord.word}",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "违禁词已删除"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PostMapping("/banned-words/batch")
    fun batchAddBannedWords(
        request: HttpServletRequest,
        @Valid @RequestBody body: AdminBatchAddBannedWordsRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val trimmedWords = StringListRules.nonBlank(body.words)
        if (trimmedWords.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "违禁词列表不能为空"))
        }

        val existingWords = adminSystemPort.existingBannedWords()
        val newWords = trimmedWords.filter { it !in existingWords }
        if (newWords.isEmpty()) {
            return ResponseEntity.ok(
                ApiResponse.ok(mapOf("added" to 0, "skipped" to trimmedWords.size), "所有违禁词均已存在，无需添加"),
            )
        }

        val adminId = adminId(request)
        adminSystemPort.addBannedWords(newWords, adminId)
        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername(request),
            action = "BATCH_ADD_BANNED_WORDS",
            targetType = "BANNED_WORD",
            targetId = null,
            details = "批量添加违禁词 ${newWords.size} 个: ${newWords.take(10).joinToString(", ")}${if (newWords.size > 10) "..." else ""}",
            ipAddress = null,
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf("added" to newWords.size, "skipped" to trimmedWords.size - newWords.size),
                "批量添加成功，新增 ${newWords.size} 个，跳过 ${trimmedWords.size - newWords.size} 个重复词",
            ),
        )
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @DeleteMapping("/banned-words/batch")
    fun batchDeleteBannedWords(
        request: HttpServletRequest,
        @Valid @RequestBody body: AdminBatchDeleteRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val existing = adminSystemPort.findBannedWordsByIds(body.ids)
        if (existing.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "没有找到需要删除的违禁词"))
        }

        adminSystemPort.deleteBannedWords(body.ids)
        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "BATCH_DELETE_BANNED_WORDS",
            targetType = "BANNED_WORD",
            targetId = null,
            details = "批量删除违禁词 ${existing.size} 个: ${existing.map { it.word }.take(10).joinToString(", ")}${if (existing.size > 10) "..." else ""}",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "批量删除成功，共删除 ${existing.size} 个违禁词"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping("/banned-words/hits")
    fun listBannedWordHits(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<PagedData<BannedWordHit>>> {
        return ResponseEntity.ok(ApiResponse.ok(adminSystemPort.listBannedWordHits(page, size, keyword)))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @DeleteMapping("/banned-words/hits/{id}")
    fun deleteBannedWordHit(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        if (!adminSystemPort.bannedWordHitExists(id)) {
            return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "命中记录不存在"))
        }
        adminSystemPort.deleteBannedWordHit(id)
        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "命中记录已删除"))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @DeleteMapping("/banned-words/hits")
    fun clearAllBannedWordHits(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val count = adminSystemPort.countBannedWordHits()
        adminSystemPort.clearBannedWordHits()
        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "CLEAR_BANNED_WORD_HITS",
            targetType = "BANNED_WORD_HIT",
            targetId = null,
            details = "清空全部命中记录，共 $count 条",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok<Unit>(message = "已清空全部命中记录（共 $count 条）"))
    }

    private fun adminId(request: HttpServletRequest): String = request.getAttribute("adminId") as String

    private fun adminUsername(request: HttpServletRequest): String =
        request.getAttribute("adminUsername") as? String ?: ""

    companion object {
        const val KEY_RED_PACKET_ENABLED = "asset.redPacket.enabled"
        const val KEY_TRANSFER_ENABLED = "asset.transfer.enabled"
    }
}
