package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.WalletConfigService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateCurrencyRequest(
    val currencyId: String,
    val name: String,
    val englishName: String,
    val icon: String,
    val type: String,
    val depositEnabled: Boolean = false,
    val withdrawEnabled: Boolean = false,
    val sortOrder: Int = 0,
)

data class UpdateCurrencyRequest(
    val name: String? = null,
    val englishName: String? = null,
    val icon: String? = null,
    val type: String? = null,
    val depositEnabled: Boolean? = null,
    val withdrawEnabled: Boolean? = null,
    val sortOrder: Int? = null,
)

data class UpdateSwitchesRequest(
    val enableDeposit: Boolean,
    val enableWithdraw: Boolean,
)

@RestController
@RequestMapping("/api/admin/wallet-config")
class AdminWalletConfigController(
    private val walletConfigService: WalletConfigService,
) {
    @RequireAdmin
    @GetMapping("/currencies")
    fun listAllCurrencies(): ResponseEntity<ApiResponse<*>> {
        return try {
            ResponseEntity.ok(ApiResponse.ok(walletConfigService.getAllCurrencies()))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.UNKNOWN_ERROR, e.message ?: "\u83b7\u53d6\u5e01\u79cd\u5217\u8868\u5931\u8d25"),
            )
        }
    }

    @RequireAdmin
    @PostMapping("/currencies")
    fun createCurrency(@RequestBody body: CreateCurrencyRequest): ResponseEntity<ApiResponse<*>> {
        if (body.currencyId.isBlank()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "currencyId \u4e0d\u80fd\u4e3a\u7a7a"),
            )
        }
        if (body.name.isBlank()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a"),
            )
        }

        return try {
            val created = walletConfigService.createCurrency(
                currencyId = body.currencyId,
                name = body.name,
                englishName = body.englishName,
                icon = body.icon,
                type = body.type,
                depositEnabled = body.depositEnabled,
                withdrawEnabled = body.withdrawEnabled,
                sortOrder = body.sortOrder,
            )
            ResponseEntity.ok(ApiResponse.ok(created, "\u5e01\u79cd\u521b\u5efa\u6210\u529f"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message ?: "\u521b\u5efa\u5931\u8d25"),
            )
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.UNKNOWN_ERROR, e.message ?: "\u521b\u5efa\u5931\u8d25"),
            )
        }
    }

    @RequireAdmin
    @PutMapping("/currencies/{id}")
    fun updateCurrency(
        @PathVariable id: String,
        @RequestBody body: UpdateCurrencyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return try {
            val updated = walletConfigService.updateCurrency(
                id = id,
                name = body.name,
                englishName = body.englishName,
                icon = body.icon,
                type = body.type,
                depositEnabled = body.depositEnabled,
                withdrawEnabled = body.withdrawEnabled,
                sortOrder = body.sortOrder,
            )
            ResponseEntity.ok(ApiResponse.ok(updated, "\u5e01\u79cd\u66f4\u65b0\u6210\u529f"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, e.message ?: "\u5e01\u79cd\u4e0d\u5b58\u5728"),
            )
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.UNKNOWN_ERROR, e.message ?: "\u66f4\u65b0\u5931\u8d25"),
            )
        }
    }

    @RequireAdmin
    @DeleteMapping("/currencies/{id}")
    fun deleteCurrency(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        return try {
            walletConfigService.deleteCurrency(id)
            ResponseEntity.ok(ApiResponse.ok<Unit>(message = "\u5e01\u79cd\u5df2\u7981\u7528"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, e.message ?: "\u5e01\u79cd\u4e0d\u5b58\u5728"),
            )
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.UNKNOWN_ERROR, e.message ?: "\u5220\u9664\u5931\u8d25"),
            )
        }
    }

    @RequireAdmin
    @GetMapping("/switches")
    fun getGlobalSwitches(): ResponseEntity<ApiResponse<*>> {
        return try {
            ResponseEntity.ok(ApiResponse.ok(walletConfigService.getGlobalSwitches()))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.UNKNOWN_ERROR, e.message ?: "\u83b7\u53d6\u5f00\u5173\u5931\u8d25"),
            )
        }
    }

    @RequireAdmin
    @PutMapping("/switches")
    fun updateGlobalSwitches(@RequestBody body: UpdateSwitchesRequest): ResponseEntity<ApiResponse<*>> {
        return try {
            walletConfigService.updateGlobalSwitches(body.enableDeposit, body.enableWithdraw)
            val switches = walletConfigService.getGlobalSwitches()
            ResponseEntity.ok(ApiResponse.ok(switches, "\u5168\u5c40\u5f00\u5173\u66f4\u65b0\u6210\u529f"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.UNKNOWN_ERROR, e.message ?: "\u66f4\u65b0\u5f00\u5173\u5931\u8d25"),
            )
        }
    }
}
