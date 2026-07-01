package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.metrics.BusinessMetrics
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicLong

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class HttpTrafficFilter(private val metrics: BusinessMetrics) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.servletPath?.takeIf { it.isNotEmpty() } ?: (request.requestURI ?: "")
        val group = classify(path)

        val inBytes = request.contentLengthLong.coerceAtLeast(0L)
        if (inBytes > 0) {
            metrics.recordHttpBytes("in", group, inBytes)
        }

        val wrapped = CountingResponseWrapper(response)
        try {
            filterChain.doFilter(request, wrapped)
            // 正常完成时主动 drain 包装器里的 OutputStreamWriter 字符缓冲，避免无换行结尾的
            // 响应体（拦截器返回的 401/403 JSON 等）滞留丢失。异常路径不 drain：留给容器/上层
            // 错误处理，避免过早提交响应。详见 CountingResponseWrapper.flushInternalWriter 注释。
            wrapped.flushInternalWriter()
        } finally {
            metrics.recordHttpBytes("out", group, wrapped.bytesWritten())
        }
    }

    private fun classify(uri: String): String = when {
        uri.startsWith("/api/media/") -> "oss-download"
        uri.startsWith("/api/upload/encrypted/") -> "api"
        uri.startsWith("/api/upload/direct/") -> "api"
        uri == "/api/upload" || uri.startsWith("/api/upload/") -> "oss-upload"
        uri.startsWith("/api/admin/") -> "admin"
        uri.startsWith("/api/") -> "api"
        else -> "other"
    }
}

class CountingResponseWrapper(private val delegate: HttpServletResponse) :
    HttpServletResponseWrapper(delegate) {

    private var counting: CountingServletOutputStream? = null
    private var writer: PrintWriter? = null
    private val written = AtomicLong(0)

    fun bytesWritten(): Long = written.get()

    override fun getOutputStream(): ServletOutputStream {
        if (writer != null) return delegate.outputStream
        val existing = counting
        if (existing != null) return existing
        val wrap = CountingServletOutputStream(delegate.outputStream, written)
        counting = wrap
        return wrap
    }

    override fun getWriter(): PrintWriter {
        if (counting != null) return delegate.writer
        val existing = writer
        if (existing != null) return existing
        val charset = delegate.characterEncoding ?: "UTF-8"
        val wrap = CountingServletOutputStream(delegate.outputStream, written)
        counting = wrap
        val w = PrintWriter(OutputStreamWriter(wrap, charset), true)
        writer = w
        return w
    }

    /**
     * 把中间 OutputStreamWriter 的字符编码缓冲刷到底层字节流。
     *
     * 根因：getWriter() 返回的是 PrintWriter(OutputStreamWriter(...))，OutputStreamWriter 自带一层
     * char→byte 编码缓冲。PrintWriter 的 autoFlush=true 只在 println/换行时触发，对
     * response.writer.write("{...无换行的 JSON...}") 这类写法不会 flush。响应结束时容器只会 flush
     * 底层响应缓冲，碰不到我们插进去的这层 OutputStreamWriter → 字节滞留、丢失，客户端收到
     * content-length: 0 的空 body（典型症状：拦截器返回的 401/403 JSON 变空）。
     * 同时 written 计数在 flush 前也是偏少的（字符还没编码进 CountingServletOutputStream）。
     */
    fun flushInternalWriter() {
        writer?.flush()
    }

    override fun flushBuffer() {
        writer?.flush()
        super.flushBuffer()
    }
}

private class CountingServletOutputStream(
    private val delegate: ServletOutputStream,
    private val counter: AtomicLong,
) : ServletOutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
        counter.incrementAndGet()
    }

    override fun write(b: ByteArray) {
        delegate.write(b)
        counter.addAndGet(b.size.toLong())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        counter.addAndGet(len.toLong())
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }

    override fun isReady(): Boolean = delegate.isReady

    override fun setWriteListener(listener: WriteListener) {
        delegate.setWriteListener(listener)
    }
}
