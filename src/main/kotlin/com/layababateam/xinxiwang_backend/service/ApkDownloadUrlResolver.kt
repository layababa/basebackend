package com.layababateam.xinxiwang_backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

@Service
class ApkDownloadUrlResolver(
    private val endpointResolver: MediaEndpointResolver,
    @Value("\${aliyun.oss.bucket-encrypted}") private val bucket: String,
) {
    fun resolve(downloadUrl: String): String {
        val uri = runCatching { URI(downloadUrl.trim()) }.getOrNull() ?: return downloadUrl
        if (uri.host.isNullOrBlank()) return downloadUrl

        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: return downloadUrl
        if (!path.lowercase().endsWith(".apk")) return downloadUrl

        val publicEndpoint = endpointResolver.currentOssPublicEndpoint().trimEnd('/')
        val objectPath = stripBucketPrefix(path)
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "$publicEndpoint$objectPath$query$fragment"
    }

    fun resolveNullable(downloadUrl: String?): String? =
        downloadUrl?.let(::resolve)

    private fun stripBucketPrefix(rawPath: String): String {
        val bucketPrefix = bucket.trim().trim('/').takeIf { it.isNotBlank() } ?: return rawPath
        val prefix = "/$bucketPrefix/"
        return if (rawPath.startsWith(prefix)) rawPath.removePrefix("/$bucketPrefix") else rawPath
    }
}
