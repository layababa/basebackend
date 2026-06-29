package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.service.AsrPort
import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AsrControllerCompatibilityTest {
    @Test
    fun `transcribe rejects non http urls before calling asr provider`() {
        var called = false
        val controller = AsrController(object : AsrPort {
            override fun transcribe(audioUrl: String, format: String): String {
                called = true
                return "unexpected"
            }
        })

        val response = controller.transcribe(request("u1"), mapOf("url" to "/var/mobile/voice.m4a"))

        assertEquals(400, response.statusCode.value())
        assertFalse(called)
        assertEquals(ErrorCode.INVALID_PARAM.code, response.body!!.code)
        assertEquals("\u97f3\u9891\u5730\u5740\u5fc5\u987b\u662f\u516c\u7f51 http(s) URL", response.body!!.message)
    }

    @Test
    fun `transcribe returns service unavailable for business degradation`() {
        val controller = AsrController(object : AsrPort {
            override fun transcribe(audioUrl: String, format: String): String {
                throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "\u8bed\u97f3\u8bc6\u522b\u670d\u52a1\u6682\u4e0d\u53ef\u7528")
            }
        })

        val response = controller.transcribe(request("u1"), mapOf("url" to "https://cdn.example.com/a.wav"))

        assertEquals(503, response.statusCode.value())
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.code, response.body!!.code)
        assertEquals("\u8bed\u97f3\u8bc6\u522b\u670d\u52a1\u6682\u4e0d\u53ef\u7528", response.body!!.message)
    }

    private fun request(userId: String): HttpServletRequest =
        Proxy.newProxyInstance(
            HttpServletRequest::class.java.classLoader,
            arrayOf(HttpServletRequest::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getAttribute" -> if (args?.get(0) == "userId") userId else null
                "toString" -> "request:$userId"
                else -> null
            }
        } as HttpServletRequest
}
