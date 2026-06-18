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
