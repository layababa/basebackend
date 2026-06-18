package com.layababateam.xinxiwang_backend.service

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * 压缩纯规则。
 *
 * 调用方负责选择载荷和后续编码格式；SDK 统一维护 deflate 生命周期。
 */
object CompressionRules {
    fun deflate(value: ByteArray, bufferSize: Int = DEFAULT_BUFFER_SIZE): ByteArray {
        val compressor = Deflater()
        return try {
            compressor.setInput(value)
            compressor.finish()
            val buffer = ByteArray(bufferSize.coerceAtLeast(1))
            val output = ByteArrayOutputStream()
            while (!compressor.finished()) {
                val count = compressor.deflate(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            compressor.end()
        }
    }

    private const val DEFAULT_BUFFER_SIZE = 4096
}
