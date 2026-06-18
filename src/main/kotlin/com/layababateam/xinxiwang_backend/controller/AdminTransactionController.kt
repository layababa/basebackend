package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.extensions.batchIn
import com.layababateam.xinxiwang_backend.extensions.escapeRegex
import com.layababateam.xinxiwang_backend.model.WalletTransaction
import com.layababateam.xinxiwang_backend.service.ExcelExportService
import com.layababateam.xinxiwang_backend.service.PaginationRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.text.SimpleDateFormat
import java.util.Date

@RestController
@RequestMapping("/api/admin/transactions")
class AdminTransactionController(
    private val mongoTemplate: MongoTemplate,
    private val excelExportService: ExcelExportService
) {

    @RequireAdmin
    @GetMapping
    fun listTransactions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) type: Int?,
        @RequestParam(required = false) startTime: Long?,
        @RequestParam(required = false) endTime: Long?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String
    ): ResponseEntity<ApiResponse<PagedData<Map<String, Any?>>>> {
        val allowedSortFields = setOf("createdAt", "amount", "type", "status")
        val safeSortBy = if (sortBy in allowedSortFields) sortBy else "createdAt"
        val safePage = PaginationRules.zeroBasedPage(page)
        val safeSize = PaginationRules.pageSize(size, 100)
        val direction = if (sortDir.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC

        val criteriaList = mutableListOf<Criteria>()

        if (!userId.isNullOrBlank()) {
            criteriaList.add(Criteria.where("userId").`is`(userId))
        }

        if (!keyword.isNullOrBlank()) {
            val safeKeyword = keyword.escapeRegex()
            val userQuery = Query()
            userQuery.addCriteria(
                Criteria().orOperator(
                    Criteria.where("username").regex(safeKeyword, "i"),
                    Criteria.where("displayName").regex(safeKeyword, "i")
                )
            )
            userQuery.fields().include("_id")
            userQuery.limit(500)
            val matchedUsers = mongoTemplate.find(userQuery, Map::class.java, "users")
            val matchedUserIds = matchedUsers.mapNotNull { it["_id"]?.toString() }

            if (matchedUserIds.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.ok(
                    PagedData(
                        items = emptyList(),
                        total = 0L,
                        page = safePage,
                        size = safeSize
                    )
                ))
            }
            criteriaList.add(Criteria.where("userId").`in`(matchedUserIds))
        }

        if (type != null) {
            criteriaList.add(Criteria.where("type").`is`(type))
        }

        if (startTime != null || endTime != null) {
            val timeCriteria = Criteria.where("createdAt")
            if (startTime != null) timeCriteria.gte(startTime)
            if (endTime != null) timeCriteria.lte(endTime)
            criteriaList.add(timeCriteria)
        }

        val query = Query()
        if (criteriaList.isNotEmpty()) {
            query.addCriteria(Criteria().andOperator(*criteriaList.toTypedArray()))
        }

        val total = mongoTemplate.count(query, "wallet_transactions")

        query.with(Sort.by(direction, safeSortBy))
        query.with(PageRequest.of(safePage, safeSize))

        val transactions = mongoTemplate.find(query, WalletTransaction::class.java)

        val allUserIds = transactions.flatMap { tx ->
            listOfNotNull(tx.userId, tx.counterpartyId)
        }.distinct()

        val userNameMap = if (allUserIds.isNotEmpty()) {
            batchIn(allUserIds) { batch ->
                val uq = Query(Criteria.where("_id").`in`(batch))
                uq.fields().include("_id").include("displayName")
                mongoTemplate.find(uq, Map::class.java, "users")
            }.associate { (it["_id"]?.toString() ?: "") to (it["displayName"]?.toString() ?: "") }
        } else {
            emptyMap()
        }

        val items = transactions.map { tx ->
            mapOf(
                "id" to tx.id,
                "userId" to tx.userId,
                "userName" to (userNameMap[tx.userId] ?: ""),
                "type" to tx.type,
                "amount" to tx.amount,
                "counterpartyId" to tx.counterpartyId,
                "counterpartyName" to tx.counterpartyName,
                "status" to tx.status,
                "txHash" to tx.txHash,
                "remark" to tx.remark,
                "createdAt" to tx.createdAt
            )
        }

        return ResponseEntity.ok(ApiResponse.ok(
            PagedData(
                items = items,
                total = total,
                page = safePage,
                size = safeSize
            )
        ))
    }

    @RequireAdmin
    @GetMapping("/export")
    fun exportTransactions(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) type: Int?,
        @RequestParam(required = false) startTime: Long?,
        @RequestParam(required = false) endTime: Long?
    ) {
        val criteriaList = mutableListOf<Criteria>()

        if (!userId.isNullOrBlank()) {
            criteriaList.add(Criteria.where("userId").`is`(userId))
        }

        if (!keyword.isNullOrBlank()) {
            val safeKw = keyword.escapeRegex()
            val userQuery = Query()
            userQuery.addCriteria(
                Criteria().orOperator(
                    Criteria.where("username").regex(safeKw, "i"),
                    Criteria.where("displayName").regex(safeKw, "i")
                )
            )
            userQuery.fields().include("_id")
            userQuery.limit(500)
            val matchedUsers = mongoTemplate.find(userQuery, Map::class.java, "users")
            val matchedUserIds = matchedUsers.mapNotNull { it["_id"]?.toString() }

            if (matchedUserIds.isEmpty()) {
                val emptyBytes = excelExportService.exportToExcel(
                    listOf("交易ID", "用户ID", "用户昵称", "类型", "金额", "对方ID", "对方昵称", "状态", "交易哈希", "备注", "时间"),
                    emptyList(),
                    "全局流水"
                )
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                response.setHeader("Content-Disposition", "attachment; filename=\"transactions_all.xlsx\"")
                response.outputStream.write(emptyBytes)
                response.outputStream.flush()
                return
            }
            criteriaList.add(Criteria.where("userId").`in`(matchedUserIds))
        }

        if (type != null) {
            criteriaList.add(Criteria.where("type").`is`(type))
        }

        if (startTime != null || endTime != null) {
            val timeCriteria = Criteria.where("createdAt")
            if (startTime != null) timeCriteria.gte(startTime)
            if (endTime != null) timeCriteria.lte(endTime)
            criteriaList.add(timeCriteria)
        }

        val query = Query()
        if (criteriaList.isNotEmpty()) {
            query.addCriteria(Criteria().andOperator(*criteriaList.toTypedArray()))
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
        query.limit(ExcelExportService.MAX_EXPORT_ROWS)

        val transactions = mongoTemplate.find(query, WalletTransaction::class.java)

        val allUserIds = transactions.flatMap { tx ->
            listOfNotNull(tx.userId, tx.counterpartyId)
        }.distinct()

        val userNameMap = if (allUserIds.isNotEmpty()) {
            batchIn(allUserIds) { batch ->
                val uq = Query(Criteria.where("_id").`in`(batch))
                uq.fields().include("_id").include("displayName")
                mongoTemplate.find(uq, Map::class.java, "users")
            }.associate { (it["_id"]?.toString() ?: "") to (it["displayName"]?.toString() ?: "") }
        } else {
            emptyMap()
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val typeNames = mapOf(
            0 to "充值", 1 to "提现", 2 to "转账收入", 3 to "转账支出",
            4 to "红包发出", 5 to "红包领取", 6 to "红包退款"
        )
        val statusNames = mapOf(0 to "处理中", 1 to "成功", 2 to "失败")

        val headers = listOf("交易ID", "用户ID", "用户昵称", "类型", "金额", "对方ID", "对方昵称", "状态", "交易哈希", "备注", "时间")
        val rows = transactions.map { tx ->
            listOf(
                tx.id,
                tx.userId,
                userNameMap[tx.userId] ?: "",
                typeNames[tx.type] ?: tx.type.toString(),
                tx.amount,
                tx.counterpartyId ?: "",
                tx.counterpartyName ?: "",
                statusNames[tx.status] ?: tx.status.toString(),
                tx.txHash ?: "",
                tx.remark,
                dateFormat.format(Date(tx.createdAt))
            )
        }

        val excelBytes = excelExportService.exportToExcel(headers, rows, "全局流水")

        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions_all.xlsx\"")
        response.outputStream.write(excelBytes)
        response.outputStream.flush()
    }
}
