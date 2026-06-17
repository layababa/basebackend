package com.layababateam.xinxiwang_backend.model

/**
 * 會話類型列舉
 *
 * PRIVATE（0）：標準一對一私聊
 * GROUP（1）：群聊
 * SPECIAL_PRIVATE（2）：特殊私聊，用於與系統官方帳號的會話（如客服、通知帳號）。
 *   業務邏輯上與一般私聊（type=0）相同。
 */
enum class ConversationType(val value: Int) {
    PRIVATE(0),
    GROUP(1),
    SPECIAL_PRIVATE(2);

    companion object {
        fun fromValue(value: Int): ConversationType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("未知的會話類型: $value")
    }
}
