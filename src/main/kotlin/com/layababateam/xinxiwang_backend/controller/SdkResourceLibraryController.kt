package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.middleware.AdminAuthInterceptor
import com.layababateam.xinxiwang_backend.service.ResourceLibraryPort
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryBatchDeleteRequest
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryBatchDeleteResponse
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryItemRequest
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryProjectRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@ConditionalOnBean(ResourceLibraryPort::class)
@RequestMapping("/api/admin/resource-library")
class SdkAdminResourceLibraryController(
    private val resourceLibraryPort: ResourceLibraryPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/projects")
    fun listProjects(): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.listAdminProjects()))

    @RequireAdmin("ADMIN")
    @PostMapping("/projects")
    fun createProject(
        request: HttpServletRequest,
        @RequestBody body: SdkResourceLibraryProjectRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.saveProject(body, null, adminName(request))))

    @RequireAdmin("ADMIN")
    @PutMapping("/projects/{id}")
    fun updateProject(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: SdkResourceLibraryProjectRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.saveProject(body, id, adminName(request))))

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/projects/{id}")
    fun deleteProject(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        resourceLibraryPort.deleteProject(id)
        return ResponseEntity.ok(ApiResponse.ok("已删除"))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/items")
    fun listItems(
        @RequestParam projectId: String,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.listAdminItems(projectId, category)))

    @RequireAdmin("ADMIN")
    @PostMapping("/items")
    fun createItem(
        request: HttpServletRequest,
        @RequestBody body: SdkResourceLibraryItemRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.saveItem(body, null, adminName(request))))

    @RequireAdmin("ADMIN")
    @PutMapping("/items/{id}")
    fun updateItem(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: SdkResourceLibraryItemRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.saveItem(body, id, adminName(request))))

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/items/{id}")
    fun deleteItem(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        resourceLibraryPort.deleteItem(id)
        return ResponseEntity.ok(ApiResponse.ok("已删除"))
    }

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/items/batch")
    fun deleteItems(@RequestBody body: SdkResourceLibraryBatchDeleteRequest): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(
            ApiResponse.ok(
                SdkResourceLibraryBatchDeleteResponse(
                    resourceLibraryPort.deleteItems(body.projectId, body.ids),
                ),
            ),
        )

    @RequireAdmin("ADMIN")
    @PostMapping("/uploads")
    fun upload(
        @RequestParam type: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.upload(type, file)))

    private fun adminName(request: HttpServletRequest): String =
        request.getAttribute(AdminAuthInterceptor.ADMIN_USERNAME_ATTR) as? String ?: "admin"
}

@RestController
@ConditionalOnBean(ResourceLibraryPort::class)
@RequestMapping("/api/resource-library")
class SdkAppResourceLibraryController(
    private val resourceLibraryPort: ResourceLibraryPort,
) {
    @GetMapping("/projects")
    fun listProjects(): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.listOpenProjects()))

    @GetMapping("/projects/{id}")
    fun getProject(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val project = resourceLibraryPort.getOpenProject(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>("项目不存在或已下线"))
        return ResponseEntity.ok(ApiResponse.ok(project))
    }

    @GetMapping("/projects/{id}/items")
    fun listItems(
        @PathVariable id: String,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.listOpenItems(id, category)))
}

@RestController
@ConditionalOnBean(ResourceLibraryPort::class)
@RequestMapping("/api/h5/resource-library")
class SdkH5ResourceLibraryController(
    private val resourceLibraryPort: ResourceLibraryPort,
) {
    @GetMapping("/projects")
    fun listProjects(): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.listOpenProjects()))

    @GetMapping("/projects/{id}")
    fun getProject(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val project = resourceLibraryPort.getOpenProject(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>("项目不存在或已下线"))
        return ResponseEntity.ok(ApiResponse.ok(project))
    }

    @GetMapping("/projects/{id}/items")
    fun listItems(
        @PathVariable id: String,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(resourceLibraryPort.listOpenItems(id, category)))
}
