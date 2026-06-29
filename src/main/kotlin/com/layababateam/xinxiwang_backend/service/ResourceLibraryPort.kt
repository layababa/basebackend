package com.layababateam.xinxiwang_backend.service

import org.springframework.web.multipart.MultipartFile

data class SdkResourceLibraryProjectRequest(
    var projectKey: String = "",
    var nameZh: String = "",
    var nameEn: String = "",
    var bannerUrl: String = "",
    var descriptionZh: String = "",
    var descriptionEn: String = "",
    var enabled: Boolean = false,
    var sort: Int = 0,
)

data class SdkResourceLibraryItemRequest(
    var projectId: String = "",
    var category: String = "",
    var titleZh: String = "",
    var titleEn: String = "",
    var coverUrl: String = "",
    var targetUrl: String = "",
    var summaryZh: String = "",
    var summaryEn: String = "",
    var enabled: Boolean = false,
    var sort: Int = 0,
)

data class SdkResourceLibraryBatchDeleteRequest(
    var projectId: String = "",
    var ids: List<String> = emptyList(),
)

data class SdkResourceLibraryBatchDeleteResponse(
    val deletedCount: Int,
)

interface ResourceLibraryPort {
    fun listAdminProjects(): List<Any>

    fun saveProject(input: SdkResourceLibraryProjectRequest, id: String?, updatedBy: String): Any

    fun deleteProject(id: String)

    fun listAdminItems(projectId: String, category: String?): List<Any>

    fun saveItem(input: SdkResourceLibraryItemRequest, id: String?, updatedBy: String): Any

    fun deleteItem(id: String)

    fun deleteItems(projectId: String, ids: List<String>): Int

    fun upload(type: String, file: MultipartFile): Any

    fun listOpenProjects(): List<Any>

    fun getOpenProject(id: String): Any?

    fun listOpenItems(projectId: String, category: String?): List<Any>
}
