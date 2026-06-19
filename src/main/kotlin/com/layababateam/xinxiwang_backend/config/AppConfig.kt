package com.layababateam.xinxiwang_backend.config

import org.springframework.context.annotation.Configuration

/**
 * Boot 4 默认装配 Jackson 3 `tools.jackson.databind.json.JsonMapper`（starter-web 默认 mapper，
 * 并自动注册 classpath 上的 Jackson 3 kotlin module），SDK 不再手动提供任何 Jackson mapper bean。
 * FAIL_ON_UNKNOWN_PROPERTIES=false / NON_NULL inclusion 等行为改由接入方 `spring.jackson.*` 配置表达。
 */
@Configuration
class AppConfig
