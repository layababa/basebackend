package com.layababateam.xinxiwang_backend.extensions

/**
 * Escape special regex metacharacters in the input string
 * to prevent NoSQL regex injection attacks.
 */
fun String.escapeRegex(): String {
    val sb = StringBuilder(this.length * 2)
    for (c in this) {
        when (c) {
            '.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\' -> {
                sb.append('\\')
                sb.append(c)
            }
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
