package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.middleware.AdminAuthInterceptor
import com.layababateam.xinxiwang_backend.service.ResourceLibraryPort
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryBatchDeleteRequest
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryItemRequest
import com.layababateam.xinxiwang_backend.service.SdkResourceLibraryProjectRequest
import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.web.multipart.MultipartFile

class SdkResourceLibraryControllerTest {
    @Test
    fun appGetProjectReturnsNotFoundWhenPortHasNoProject() {
        val controller = SdkAppResourceLibraryController(FakeResourceLibraryPort(openProject = null))

        val response = controller.getProject("project-missing")

        assertEquals(404, response.statusCode.value())
        assertFalse(response.body!!.success)
        assertEquals("项目不存在或已下线", response.body!!.message)
        assertNull(response.body!!.data)
    }

    @Test
    fun h5ListItemsDelegatesProjectAndCategoryToPort() {
        val port = FakeResourceLibraryPort()
        val controller = SdkH5ResourceLibraryController(port)

        val response = controller.listItems(id = "project-1", category = "guide")

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(listOf(mapOf("titleZh" to "Guide")), response.body!!.data)
        assertEquals(ListItemsCall(projectId = "project-1", category = "guide"), port.openItemCalls.single())
    }

    @Test
    fun adminCreateProjectUsesAdminUsername() {
        val port = FakeResourceLibraryPort()
        val controller = SdkAdminResourceLibraryController(port)

        val response = controller.createProject(
            request = resourceRequest(
                attributes = mapOf(AdminAuthInterceptor.ADMIN_USERNAME_ATTR to "editor"),
            ),
            body = SdkResourceLibraryProjectRequest(projectKey = "p1", nameZh = "Library"),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(SaveProjectCall(id = null, updatedBy = "editor", projectKey = "p1"), port.saveProjectCalls.single())
    }

    @Test
    fun adminBatchDeleteReturnsDeletedCountFromPort() {
        val port = FakeResourceLibraryPort(deletedCount = 2)
        val controller = SdkAdminResourceLibraryController(port)

        val response = controller.deleteItems(
            SdkResourceLibraryBatchDeleteRequest(projectId = "project-1", ids = listOf("item-1", "item-2")),
        )

        assertEquals(200, response.statusCode.value())
        assertTrue(response.body!!.success)
        assertEquals(DeleteItemsCall("project-1", listOf("item-1", "item-2")), port.deleteItemsCalls.single())
        val data = response.body!!.data as com.layababateam.xinxiwang_backend.service.SdkResourceLibraryBatchDeleteResponse
        assertEquals(2, data.deletedCount)
    }
}

private data class ListItemsCall(val projectId: String, val category: String?)

private data class SaveProjectCall(val id: String?, val updatedBy: String, val projectKey: String)

private data class DeleteItemsCall(val projectId: String, val ids: List<String>)

private class FakeResourceLibraryPort(
    private val openProject: Any? = mapOf("id" to "project-1"),
    private val deletedCount: Int = 1,
) : ResourceLibraryPort {
    val openItemCalls = mutableListOf<ListItemsCall>()
    val saveProjectCalls = mutableListOf<SaveProjectCall>()
    val deleteItemsCalls = mutableListOf<DeleteItemsCall>()

    override fun listAdminProjects(): List<Any> =
        listOf(mapOf("id" to "project-1"))

    override fun saveProject(input: SdkResourceLibraryProjectRequest, id: String?, updatedBy: String): Any {
        saveProjectCalls += SaveProjectCall(id = id, updatedBy = updatedBy, projectKey = input.projectKey)
        return mapOf("id" to (id ?: "project-1"))
    }

    override fun deleteProject(id: String) = Unit

    override fun listAdminItems(projectId: String, category: String?): List<Any> =
        listOf(mapOf("projectId" to projectId, "category" to category))

    override fun saveItem(input: SdkResourceLibraryItemRequest, id: String?, updatedBy: String): Any =
        mapOf("id" to (id ?: "item-1"), "updatedBy" to updatedBy, "projectId" to input.projectId)

    override fun deleteItem(id: String) = Unit

    override fun deleteItems(projectId: String, ids: List<String>): Int {
        deleteItemsCalls += DeleteItemsCall(projectId, ids)
        return deletedCount
    }

    override fun upload(type: String, file: MultipartFile): Any =
        mapOf("type" to type, "filename" to file.originalFilename)

    override fun listOpenProjects(): List<Any> =
        listOf(mapOf("id" to "project-1"))

    override fun getOpenProject(id: String): Any? =
        openProject

    override fun listOpenItems(projectId: String, category: String?): List<Any> {
        openItemCalls += ListItemsCall(projectId, category)
        return listOf(mapOf("titleZh" to "Guide"))
    }
}

private fun resourceRequest(attributes: Map<String, Any?> = emptyMap()): HttpServletRequest =
    Proxy.newProxyInstance(
        HttpServletRequest::class.java.classLoader,
        arrayOf(HttpServletRequest::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getAttribute" -> attributes[args?.firstOrNull()]
            else -> defaultResourceControllerValue(method.returnType)
        }
    } as HttpServletRequest

private fun defaultResourceControllerValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        else -> null
    }
