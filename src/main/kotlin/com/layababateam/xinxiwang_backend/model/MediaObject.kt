package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * One row per uploaded encrypted media object.  Stores enough metadata for the
 * server to:
 *  - look the object up by [mediaId] and stream it through the proxy,
 *  - know which master key was used so [MediaCryptoService] can decrypt it,
 *  - present catalog/list responses without re-fetching the binary,
 *  - GC orphaned objects after their owning conversation/message is deleted.
 *
 * The model itself is immutable; updates are performed by replacing the
 * document with a fresh copy via [data class .copy].
 */
@Document(collection = "media_objects")
data class MediaObject(
    @Id val id: String? = null,
    @Indexed(unique = true) val mediaId: String,
    @Indexed val ownerId: String,
    val conversationId: String? = null,
    val category: String,            // images / videos / audio / files
    val mime: String,
    val ext: String,                 // file extension without the leading dot
    val ossBucket: String,
    val ossKey: String,
    val thumbOssKey: String? = null,
    val plainOssKey: String? = null,
    val plainThumbOssKey: String? = null,
    val keyId: String,
    val alg: String = "AES-256-GCM",
    val plainSize: Long,
    val cipherSize: Long,
    val thumbPlainSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val decryptedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
