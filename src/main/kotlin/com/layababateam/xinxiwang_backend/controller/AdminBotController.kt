package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.repository.BotRepository
import com.layababateam.xinxiwang_backend.service.AdminBotManagementPort
import com.layababateam.xinxiwang_backend.service.PaginationRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
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

data class CreateBotRequest(
    @field:NotBlank(message = "用户名不能为空")
    @field:Size(min = 3, max = 25, message = "用户名长度 3-25")
    val username: String,

    @field:NotBlank(message = "显示名称不能为空")
    @field:Size(max = 30, message = "显示名称不超过 30 字")
    val displayName: String,

    @field:Size(max = 500)
    val avatarUrl: String = "",
    @field:Size(max = 200)
    val description: String = "",
)

data class UpdateBotRequest(
    @field:Size(max = 30)
    val displayName: String? = null,
    @field:Size(max = 500)
    val avatarUrl: String? = null,
    @field:Size(max = 200)
    val description: String? = null,
)

data class UpdateBotStatusRequest(
    val status: Int,
)

@RestController
@RequestMapping("/api/admin/bots")
class AdminBotController(
    private val botManagementPort: AdminBotManagementPort,
    private val botRepository: BotRepository,
    private val mongoTemplate: MongoTemplate,
) {

    @RequireAdmin
    @GetMapping
    fun listBots(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<*>> {
        val query = Query()
        if (!keyword.isNullOrBlank()) {
            query.addCriteria(
                Criteria().orOperator(
                    Criteria.where("username").regex(keyword, "i"),
                    Criteria.where("displayName").regex(keyword, "i"),
                ),
            )
        }
        val total = mongoTemplate.count(query, "bots")
        val safePage = PaginationRules.zeroBasedPage(page)
        val safeSize = PaginationRules.pageSize(size, 100)
        query.with(PageRequest.of(safePage, safeSize))
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))

        val bots = mongoTemplate.find(query, Map::class.java, "bots").map {
            it.toMutableMap().apply {
                this["_id"] = this["_id"]?.toString()
                this.remove("apiKeyHash")
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(PagedData(items = bots, total = total, page = safePage, size = safeSize)))
    }

    @RequireAdmin
    @PostMapping
    fun createBot(
        request: HttpServletRequest,
        @Valid @RequestBody body: CreateBotRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val adminId = request.getAttribute("adminId") as String
        return try {
            val result = botManagementPort.createManagedBot(
                adminId,
                body.username,
                body.displayName,
                body.avatarUrl,
                body.description,
            )
            ResponseEntity.ok(
                ApiResponse.ok(
                    mapOf(
                        "bot" to result.bot,
                        "apiKey" to result.apiKey,
                    ),
                    "Bot 创建成功，请保存 API Key（仅显示一次）",
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
        }
    }

    @RequireAdmin
    @GetMapping("/{id}")
    fun getBotDetail(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val bot = botRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "Bot 不存在"))
        return ResponseEntity.ok(ApiResponse.ok(bot))
    }

    @RequireAdmin
    @PutMapping("/{id}")
    fun updateBot(
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateBotRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return try {
            val updated = botManagementPort.updateManagedBot(id, body.displayName, body.avatarUrl, body.description)
            ResponseEntity.ok(ApiResponse.ok(updated))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
        }
    }

    @RequireAdmin
    @PostMapping("/{id}/regenerate-key")
    fun regenerateKey(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        return try {
            val newKey = botManagementPort.regenerateManagedBotApiKey(id)
            ResponseEntity.ok(ApiResponse.ok(mapOf("apiKey" to newKey), "API Key 已重新生成，请保存（仅显示一次）"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
        }
    }

    @RequireAdmin
    @PutMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: String,
        @RequestBody body: UpdateBotStatusRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return try {
            val updated = botManagementPort.updateManagedBotStatus(id, body.status)
            ResponseEntity.ok(ApiResponse.ok(updated))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
        }
    }

    @RequireAdmin
    @DeleteMapping("/{id}")
    fun deleteBot(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        botManagementPort.deleteManagedBot(id)
        return ResponseEntity.ok(ApiResponse.ok<Any>(message = "Bot 已删除"))
    }
}
