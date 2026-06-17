package com.layababateam.xinxiwang_backend.config

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAdmin(val role: String = "ADMIN")
