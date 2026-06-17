package com.layababateam.xinxiwang_backend.extensions

private const val DEFAULT_BATCH_SIZE = 500

/**
 * Splits a large ID list into batches and executes the query for each batch,
 * merging results. Prevents MongoDB $in queries from exceeding safe limits.
 */
fun <T> batchIn(
    ids: Collection<String>,
    batchSize: Int = DEFAULT_BATCH_SIZE,
    query: (List<String>) -> List<T>
): List<T> {
    if (ids.isEmpty()) return emptyList()
    val idList = ids.toList()
    if (idList.size <= batchSize) return query(idList)
    return idList.chunked(batchSize).flatMap(query)
}
