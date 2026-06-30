package com.layababateam.xinxiwang_backend.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.test.Test
import kotlin.test.assertTrue

class AdminUserCustomerServiceControllerTest {
    @Test
    fun `sdk provides admin user customer service toggle route`() {
        val controllerClass = Class.forName(
            "com.layababateam.xinxiwang_backend.controller.AdminUserCustomerServiceController",
        )
        val requestMapping = controllerClass.getAnnotation(RequestMapping::class.java)
        assertTrue(requestMapping.value.contains("/api/admin/users"))

        val toggle = controllerClass.declaredMethods.single { it.name == "toggleCustomerService" }
        val postMapping = toggle.getAnnotation(PostMapping::class.java)
        assertTrue(postMapping.value.contains("/{id}/customer-service"))
    }
}
