package com.layababateam.xinxiwang_backend.service

/**
 * 分页参数纯规则。
 *
 * 这里只处理页码、页大小和内存分页偏移量；业务侧负责构造 PageRequest 或返回体。
 */
object PaginationRules {
    fun zeroBasedPage(page: Int): Int =
        page.coerceAtLeast(0)

    fun oneBasedToZeroBasedPage(page: Int): Int =
        (page - 1).coerceAtLeast(0)

    fun pageSize(size: Int, max: Int, min: Int = 1): Int =
        size.coerceIn(min, max)

    fun offset(page: Int, pageSize: Int): Int =
        zeroBasedPage(page) * pageSize.coerceAtLeast(0)

    fun oneBasedOffset(page: Int, pageSize: Int): Int =
        oneBasedToZeroBasedPage(page) * pageSize.coerceAtLeast(0)
}
