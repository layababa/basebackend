package com.layababateam.xinxiwang_backend.exception

import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy

/**
 * 最小 HttpServletRequest 测试桩：用 JDK 动态代理返回安全默认值。
 * GlobalExceptionHandler 只读 method / requestURI / 少量 header，均返回非破坏性默认值即可。
 */
object StubHttpServletRequestFactory {
    fun create(): HttpServletRequest = Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, _ ->
        when (method.returnType) {
            String::class.java -> when (method.name) {
                "getMethod" -> "POST"
                "getRequestURI" -> "/test"
                "getRemoteAddr" -> "127.0.0.1"
                else -> ""
            }
            Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
            Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
            Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
            else -> null
        }
    } as HttpServletRequest
}

@Suppress("FunctionName")
fun StubHttpServletRequest(): HttpServletRequest = StubHttpServletRequestFactory.create()
