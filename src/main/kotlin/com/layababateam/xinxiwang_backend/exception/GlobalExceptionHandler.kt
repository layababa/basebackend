package com.layababateam.xinxiwang_backend.exception

import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.RequestMetadataRules
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceConflictException
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    private fun captureServerError(
        ex: Exception,
        request: HttpServletRequest?,
        errorType: String,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setTag("error.type", errorType)

            if (request != null) {
                scope.setTag("request.method", request.method)
                scope.setTag("request.uri", request.requestURI)
                scope.setExtra("request.ip", resolveClientIp(request))
                scope.setExtra("request.userAgent", request.getHeader("User-Agent") ?: "unknown")
                scope.setExtra("request.queryString", request.queryString ?: "")
            }

            extras.forEach { (key, value) ->
                scope.setExtra(key, (value ?: "null").toString())
            }

            Sentry.captureException(ex)
        }
    }

    private fun captureClientWarning(
        ex: Exception,
        request: HttpServletRequest?,
        errorType: String,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "exception"
            message = "$errorType: ${ex.message}"
            level = SentryLevel.WARNING
        })

        Sentry.withScope { scope ->
            scope.level = SentryLevel.WARNING
            scope.setTag("error.type", errorType)

            if (request != null) {
                scope.setTag("request.method", request.method)
                scope.setTag("request.uri", request.requestURI)
            }

            extras.forEach { (key, value) ->
                scope.setExtra(key, (value ?: "null").toString())
            }

            Sentry.captureException(ex)
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        return RequestMetadataRules.clientIp(
            forwardedFor = request.getHeader("X-Forwarded-For"),
            realIp = request.getHeader("X-Real-IP"),
            remoteAddr = request.remoteAddr,
        ).orEmpty()
    }

    private fun buildValidationMessage(prefix: String, errors: Map<String, String>): String {
        val firstMessage = errors.values.firstOrNull()
        if (firstMessage.isNullOrBlank()) return prefix
        return if (errors.size > 1) {
            "$firstMessage（另有 ${errors.size - 1} 项参数需要检查）"
        } else {
            firstMessage
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Map<String, String>>> {
        val errors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "验证失败")
        }
        captureClientWarning(ex, request, "validation_error", mapOf("fields" to errors))
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ApiResponse(
                    success = false,
                    message = buildValidationMessage("输入验证失败", errors),
                    data = errors,
                )
            )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Map<String, String>>> {
        val errors = ex.constraintViolations.associate {
            it.propertyPath.toString() to (it.message ?: "验证失败")
        }
        captureClientWarning(ex, request, "constraint_violation", mapOf("fields" to errors))
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ApiResponse(
                    success = false,
                    message = buildValidationMessage("参数验证失败", errors),
                    data = errors,
                )
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("JSON 反序列化失败: {}", ex.message)
        val message = extractReadableMessage(ex)
        captureClientWarning(ex, request, "json_deserialization_error", mapOf("detail" to message))
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse(success = false, message = message))
    }

    private fun extractReadableMessage(ex: HttpMessageNotReadableException): String {
        return when (val cause = ex.cause) {
            // 注意分支顺序：UnrecognizedPropertyException 与 InvalidFormatException 都是
            // MismatchedInputException 的子类（Jackson 3 hierarchy），必须在 MismatchedInputException
            // 之前匹配，否则会被通用分支吞掉（迁移后由 GlobalExceptionHandlerTest 坐实）。
            is UnrecognizedPropertyException -> {
                "未知字段「${cause.propertyName}」"
            }
            is InvalidFormatException -> {
                val field = cause.path.joinToString(".") { it.propertyName ?: "[${it.index}]" }
                val targetType = cause.targetType?.simpleName ?: "未知"
                if (field.isNotEmpty()) {
                    "字段「$field」的值格式不正确，期望类型为 $targetType"
                } else {
                    "值格式不正确，期望类型为 $targetType"
                }
            }
            is MismatchedInputException -> {
                val field = cause.path.joinToString(".") { it.propertyName ?: "[${it.index}]" }
                if (field.isNotEmpty()) {
                    // Jackson 3 已删除 MissingKotlinParameterException 并并入 MismatchedInputException
                    // （FasterXML/jackson-module-kotlin #617），改按 message 语义判定「缺少必填字段」。
                    val msg = cause.message.orEmpty()
                    val isMissing = msg.contains("missing", ignoreCase = true) ||
                        msg.contains("null value for creator", ignoreCase = true) ||
                        msg.contains("due to missing", ignoreCase = true)
                    if (isMissing) "缺少必填字段「$field」" else "字段「$field」的类型不匹配"
                } else {
                    "请求参数类型不匹配"
                }
            }
            is StreamReadException -> {
                "JSON 格式错误，请检查语法"
            }
            else -> "请求格式错误，请检查 JSON 内容"
        }
    }

    /**
     * 兜底处理 HttpMessageConversionException（HttpMessageNotReadableException 是其子类，已被上面的更具体 handler 拦截）。
     * 典型来源：Jackson 无法构造目标类型（如 KotlinModule 未生效时的 InvalidDefinitionException「no Creators」）。
     * 此前这类异常会落到 handleGenericException → 返回 500/10000，掩盖真实原因；此处统一收成 400。
     */
    @ExceptionHandler(HttpMessageConversionException::class)
    fun handleMessageConversion(
        ex: HttpMessageConversionException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("消息转换异常: {}", ex.message)
        captureClientWarning(ex, request, "message_conversion_error", mapOf(
            "exceptionClass" to ex.javaClass.name,
        ))
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.INVALID_PARAM, "请求数据格式不正确，请检查后重试"))
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = when (ex.errorCode) {
            ErrorCode.UNAUTHORIZED, ErrorCode.TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED
            ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN
            ErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCode.SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
            else -> HttpStatus.BAD_REQUEST
        }

        if (ex.errorCode == ErrorCode.SERVICE_UNAVAILABLE) {
            captureServerError(ex, request, "business_error", mapOf(
                "errorCode" to ex.errorCode.code,
                "errorName" to ex.errorCode.name,
            ))
        } else {
            captureClientWarning(ex, request, "business_error", mapOf(
                "errorCode" to ex.errorCode.code,
                "errorName" to ex.errorCode.name,
            ))
        }

        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ex.errorCode, ex.message))
    }

    @ExceptionHandler(WebCustomerServiceConflictException::class)
    fun handleWebCustomerServiceConflict(
        ex: WebCustomerServiceConflictException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        captureClientWarning(ex, request, "web_customer_service_conflict")
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.INVALID_PARAM, ex.message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        captureClientWarning(ex, request, "illegal_argument")
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.INVALID_PARAM, ex.message ?: "参数错误"))
    }

    @ExceptionHandler(ClassCastException::class)
    fun handleClassCast(
        ex: ClassCastException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("类型转换异常: {}", ex.message)
        captureServerError(ex, request, "class_cast_exception")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.UNAUTHORIZED, "认证信息无效，请重新登录"))
    }

    @ExceptionHandler(NullPointerException::class)
    fun handleNullPointer(
        ex: NullPointerException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("空指针异常: ", ex)
        captureServerError(ex, request, "null_pointer_exception")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.UNKNOWN_ERROR, "服务器内部错误"))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.info("404 资源不存在: method={} uri={}", request.method, request.requestURI)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.NOT_FOUND, "资源不存在"))
    }

    @ExceptionHandler(java.net.SocketException::class)
    fun handleSocketException(
        ex: java.net.SocketException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Socket 异常（环境问题）: {}", ex.message)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.SERVICE_UNAVAILABLE, "网络暂时不可达"))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        captureClientWarning(ex, request, "method_not_allowed", mapOf(
            "rejectedMethod" to ex.method,
            "supportedMethods" to (ex.supportedMethods?.joinToString() ?: "unknown"),
        ))
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED, "不支持 ${ex.method} 请求方法"))
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException::class)
    fun handleDataAccess(ex: org.springframework.dao.DataAccessException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("DataAccessException", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.DATABASE_ERROR))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("未处理异常: ", ex)
        captureServerError(ex, request, "unhandled_exception", mapOf(
            "exceptionClass" to ex.javaClass.name,
        ))
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.error(ErrorCode.UNKNOWN_ERROR))
    }
}
