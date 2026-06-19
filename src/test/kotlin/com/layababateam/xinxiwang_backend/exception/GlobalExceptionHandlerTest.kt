package com.layababateam.xinxiwang_backend.exception

import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpHeaders
import org.springframework.http.converter.HttpMessageNotReadableException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 🔴 T4.2 护栏：坐实 Jackson 2→3 迁移后 GlobalExceptionHandler.extractReadableMessage 仍正确分流。
 *
 * 关键点：MissingKotlinParameterException 在 Jackson 3 已删除并入 MismatchedInputException
 * （FasterXML/jackson-module-kotlin #617）。本测试用真实 Jackson 3 JsonMapper + kotlin module
 * 反序列化触发真实异常，验证「缺少必填字段」判定按 Jackson 3 实跑 message 命中（而非退化为类型不匹配）。
 *
 * 由于 extractReadableMessage 私有，通过 public 入口 handleMessageNotReadable 驱动，
 * 拿真实 HttpMessageNotReadableException(cause=真实 Jackson 3 异常) 走完整分流逻辑。
 */
class GlobalExceptionHandlerTest {

    data class SampleDto(val name: String, val age: Int)

    private val handler = GlobalExceptionHandler()
    private val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private fun httpInputMessage(body: String): HttpInputMessage = object : HttpInputMessage {
        override fun getBody(): InputStream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getHeaders(): HttpHeaders = HttpHeaders()
    }

    // 严格 mapper：显式开启 FAIL_ON_UNKNOWN_PROPERTIES，复现 UnrecognizedPropertyException。
    // Jackson 3 默认该 feature 为 false（未知字段不报错），接入方实际是否报错取决于 spring.jackson.* 配置。
    private val strictMapper: JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    /** 触发真实 Jackson 3 反序列化异常并包成 Spring 的 HttpMessageNotReadableException。 */
    private fun deserializeFailure(json: String, m: JsonMapper = mapper): HttpMessageNotReadableException {
        val cause = try {
            m.readValue(json, SampleDto::class.java)
            error("expected deserialization to fail for: $json")
        } catch (e: Exception) {
            e
        }
        return HttpMessageNotReadableException("not readable", cause, httpInputMessage(json))
    }

    private fun messageFor(json: String, m: JsonMapper = mapper): String {
        val ex = deserializeFailure(json, m)
        val resp = handler.handleMessageNotReadable(ex, StubHttpServletRequest())
        return resp.body!!.message
    }

    @Test
    fun `missing non-null kotlin parameter returns missing-field message not type-mismatch`() {
        // 缺失非空 Kotlin 参数 name → Jackson 3 抛 MismatchedInputException
        val msg = messageFor("""{"age":30}""")
        assertTrue(
            msg.contains("缺少必填字段"),
            "期望「缺少必填字段」，实际：$msg —— 若退化为类型不匹配说明 Jackson 3 message 关键字未对齐",
        )
    }

    @Test
    fun `type mismatch returns type-mismatch message`() {
        // age 给字符串 "abc" → InvalidFormatException（MismatchedInputException 子类）
        val msg = messageFor("""{"name":"bob","age":"abc"}""")
        assertTrue(
            msg.contains("格式不正确") || msg.contains("类型不匹配"),
            "期望类型/格式错误提示，实际：$msg",
        )
        assertTrue(!msg.contains("缺少必填字段"), "类型不匹配不应误报为缺少必填字段：$msg")
    }

    @Test
    fun `malformed json hits StreamReadException branch`() {
        val msg = messageFor("""{"name":"bob", "age": }""") // 语法错误
        assertTrue(msg.contains("JSON 格式错误"), "期望「JSON 格式错误」，实际：$msg")
    }

    @Test
    fun `unknown property maps to unknown-field message under strict mapper`() {
        // 用严格 mapper 复现 UnrecognizedPropertyException → 验证「未知字段」分支
        val msg = messageFor("""{"name":"bob","age":1,"extra":true}""", strictMapper)
        assertTrue(msg.contains("未知字段「extra」"), "期望「未知字段「extra」」，实际：$msg")
    }
}
