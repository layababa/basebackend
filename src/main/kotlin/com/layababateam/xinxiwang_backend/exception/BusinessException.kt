package com.layababateam.xinxiwang_backend.exception

import com.layababateam.xinxiwang_backend.dto.ErrorCode

open class BusinessException(
    val errorCode: ErrorCode,
    message: String? = null
) : RuntimeException(message ?: errorCode.defaultMessage)

class UnauthorizedException(message: String? = null) :
    BusinessException(ErrorCode.UNAUTHORIZED, message)

class ForbiddenException(message: String? = null) :
    BusinessException(ErrorCode.FORBIDDEN, message)

class NotFoundException(message: String? = null) :
    BusinessException(ErrorCode.NOT_FOUND, message)
