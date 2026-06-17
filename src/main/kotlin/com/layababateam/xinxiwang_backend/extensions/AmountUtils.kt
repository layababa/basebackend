package com.layababateam.xinxiwang_backend.extensions

import java.math.BigDecimal

/**
 * 金額安全轉換擴展函數。
 * 所有金額欄位在 MongoDB 中以 String 儲存，運算前必須透過這些方法
 * 安全轉為 BigDecimal，避免 NumberFormatException 導致服務崩潰。
 */

/** 將字串安全轉換為 BigDecimal，驗證格式與精度 */
fun String.toSafeAmount(): BigDecimal {
    if (this.isBlank()) {
        throw IllegalArgumentException("金额不能为空")
    }
    return try {
        BigDecimal(this).also { bd ->
            require(bd.scale() <= 18) { "金额精度超出限制: $this" }
        }
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("无效的金额格式: $this", e)
    }
}

/** 驗證金額為正數並返回 BigDecimal */
fun String.toPositiveAmount(): BigDecimal {
    val bd = this.toSafeAmount()
    require(bd > BigDecimal.ZERO) { "金额必须大于0" }
    return bd
}
