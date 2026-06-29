package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.repository.ServerNodeRepository
import java.lang.reflect.Proxy

internal fun emptyServerNodeRepository(): ServerNodeRepository =
    Proxy.newProxyInstance(
        ServerNodeRepository::class.java.classLoader,
        arrayOf(ServerNodeRepository::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "findByEnabledTrueOrderBySortOrderAsc" -> emptyList<Any>()
            "toString" -> "EmptyServerNodeRepository"
            else -> throw UnsupportedOperationException(method.name)
        }
    } as ServerNodeRepository
