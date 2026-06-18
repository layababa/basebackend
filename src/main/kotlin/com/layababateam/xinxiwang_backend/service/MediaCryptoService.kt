package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Parsed media-cipher header.
 *
 * Supported on-disk formats:
 * - LKE1: static AES-GCM payloads.
 * - LKE2: chunked video header parsing; chunk encryption/decryption is still intentionally stubbed.
 */
data class MediaCipherHeader(
    val magic: String,
    val version: Int,
    val keyId: String,
    val nonce: ByteArray,
    val chunkSize: Int? = null,
    val totalChunks: Int? = null,
    val headerLength: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaCipherHeader) return false
        return magic == other.magic &&
            version == other.version &&
            keyId == other.keyId &&
            nonce.contentEquals(other.nonce) &&
            chunkSize == other.chunkSize &&
            totalChunks == other.totalChunks &&
            headerLength == other.headerLength
    }

    override fun hashCode(): Int {
        var result = magic.hashCode()
        result = 31 * result + version
        result = 31 * result + keyId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + (chunkSize ?: 0)
        result = 31 * result + (totalChunks ?: 0)
        result = 31 * result + headerLength
        return result
    }
}

/**
 * AES-256-GCM encryption/decryption for media payloads.
 *
 * The media id is bound into GCM AAD so ciphertext copied to a different media id
 * fails authentication. Master key selection is stored in the payload header.
 */
@Service
class MediaCryptoService(
    private val keyRegistry: MediaKeyRegistry,
) {
    fun encryptStatic(
        plain: ByteArray,
        mediaId: String,
        keyId: String = keyRegistry.currentKeyId(),
    ): ByteArray {
        val key = keyRegistry.keyById(keyId) ?: error("Unknown keyId: $keyId")
        val nonce = ByteArray(NONCE_LEN).also { RNG.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aadFor(mediaId))
        val ciphertext = cipher.doFinal(plain)

        val keyIdBytes = keyId.toByteArray(StandardCharsets.US_ASCII)
        require(keyIdBytes.size in 1..255) { "keyId byte length must fit in one unsigned byte" }

        val out = ByteArrayOutputStream(MAGIC_LEN + 1 + 1 + keyIdBytes.size + NONCE_LEN + ciphertext.size)
        out.write(MAGIC_STATIC.toByteArray(StandardCharsets.US_ASCII))
        out.write(byteArrayOf(VERSION_STATIC))
        out.write(byteArrayOf(keyIdBytes.size.toByte()))
        out.write(keyIdBytes)
        out.write(nonce)
        out.write(ciphertext)
        return out.toByteArray()
    }

    fun decryptStatic(blob: ByteArray, mediaId: String): ByteArray {
        val header = parseHeader(blob)
        require(header.magic == MAGIC_STATIC) { "Not LKE1 blob: ${header.magic}" }
        val key = keyRegistry.keyById(header.keyId) ?: error("Unknown keyId: ${header.keyId}")
        val ciphertext = blob.copyOfRange(header.headerLength, blob.size)

        try {
            return doGcmDecrypt(key, header.nonce, ciphertext, aadFor(mediaId))
        } catch (_: javax.crypto.AEADBadTagException) {
            // Fall through to legacy AAD prefixes used before the media namespace rename.
        }

        for (legacy in LEGACY_AAD_PREFIXES) {
            try {
                return doGcmDecrypt(key, header.nonce, ciphertext, (legacy + mediaId).toByteArray(StandardCharsets.UTF_8))
            } catch (_: javax.crypto.AEADBadTagException) {
                continue
            }
        }

        return doGcmDecrypt(key, header.nonce, ciphertext, aadFor(mediaId))
    }

    fun decryptStaticFile(cipherFile: File, plainFile: File, mediaId: String) {
        val header = parseHeader(readHeaderBytes(cipherFile))
        require(header.magic == MAGIC_STATIC) { "Not LKE1 blob: ${header.magic}" }
        val key = keyRegistry.keyById(header.keyId) ?: error("Unknown keyId: ${header.keyId}")

        try {
            doGcmDecryptFile(cipherFile, plainFile, header, key, aadFor(mediaId))
            return
        } catch (_: javax.crypto.AEADBadTagException) {
            plainFile.delete()
        }

        for (legacy in LEGACY_AAD_PREFIXES) {
            try {
                doGcmDecryptFile(cipherFile, plainFile, header, key, (legacy + mediaId).toByteArray(StandardCharsets.UTF_8))
                return
            } catch (_: javax.crypto.AEADBadTagException) {
                plainFile.delete()
            }
        }

        doGcmDecryptFile(cipherFile, plainFile, header, key, aadFor(mediaId))
    }

    fun parseHeader(blob: ByteArray): MediaCipherHeader {
        require(blob.size >= MAGIC_LEN + 1 + 1) { "Blob too short" }
        val magic = String(blob, 0, MAGIC_LEN, StandardCharsets.US_ASCII)
        val version = blob[MAGIC_LEN].toInt() and 0xff
        val keyIdLen = blob[MAGIC_LEN + 1].toInt() and 0xff
        var offset = MAGIC_LEN + 2
        require(blob.size >= offset + keyIdLen) { "Blob truncated in keyId" }
        val keyId = String(blob, offset, keyIdLen, StandardCharsets.US_ASCII)
        offset += keyIdLen
        return when (magic) {
            MAGIC_STATIC -> {
                require(blob.size >= offset + NONCE_LEN) { "Blob truncated in nonce" }
                val nonce = blob.copyOfRange(offset, offset + NONCE_LEN)
                offset += NONCE_LEN
                MediaCipherHeader(magic, version, keyId, nonce, headerLength = offset)
            }
            MAGIC_VIDEO -> {
                require(blob.size >= offset + NONCE_LEN + 8) { "Blob truncated in video header" }
                val nonce = blob.copyOfRange(offset, offset + NONCE_LEN)
                offset += NONCE_LEN
                val chunkSize = ByteBuffer.wrap(blob, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                offset += 4
                val totalChunks = ByteBuffer.wrap(blob, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                offset += 4
                MediaCipherHeader(magic, version, keyId, nonce, chunkSize, totalChunks, offset)
            }
            else -> error("Unknown magic: $magic")
        }
    }

    fun encryptVideoChunked(
        @Suppress("UNUSED_PARAMETER") plain: ByteArray,
        @Suppress("UNUSED_PARAMETER") mediaId: String,
        @Suppress("UNUSED_PARAMETER") chunkSize: Int = DEFAULT_VIDEO_CHUNK_SIZE,
        @Suppress("UNUSED_PARAMETER") keyId: String = keyRegistry.currentKeyId(),
    ): ByteArray {
        throw NotImplementedError("LKE2 chunked video encryption not implemented yet (planned for v2)")
    }

    fun decryptVideoChunk(
        @Suppress("UNUSED_PARAMETER") blob: ByteArray,
        @Suppress("UNUSED_PARAMETER") mediaId: String,
        @Suppress("UNUSED_PARAMETER") chunkIndex: Int,
    ): ByteArray {
        throw NotImplementedError("LKE2 chunked video decryption not implemented yet (planned for v2)")
    }

    private fun doGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun doGcmDecryptFile(
        cipherFile: File,
        plainFile: File,
        header: MediaCipherHeader,
        key: ByteArray,
        aad: ByteArray,
    ) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, header.nonce))
        cipher.updateAAD(aad)

        cipherFile.inputStream().use { input ->
            plainFile.outputStream().use { output ->
                input.skipNBytes(header.headerLength.toLong())
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val out = cipher.update(buffer, 0, read)
                    if (out != null && out.isNotEmpty()) output.write(out)
                }
                output.write(cipher.doFinal())
            }
        }
    }

    private fun readHeaderBytes(file: File): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            require(raf.length() >= (MAGIC_LEN + 1 + 1).toLong()) { "Blob too short" }
            val prefix = ByteArray(MAGIC_LEN + 2)
            raf.readFully(prefix)
            val keyIdLen = prefix[MAGIC_LEN + 1].toInt() and 0xff
            val headerLenUpperBound = MAGIC_LEN + 2 + keyIdLen + NONCE_LEN + 8
            require(raf.length() >= (MAGIC_LEN + 2 + keyIdLen).toLong()) { "Blob truncated in keyId" }
            val bytesToRead = minOf(raf.length(), headerLenUpperBound.toLong()).toInt()
            val header = ByteArray(bytesToRead)
            raf.seek(0)
            raf.readFully(header)
            return header
        }
    }

    private fun aadFor(mediaId: String): ByteArray =
        (AAD_PREFIX + mediaId).toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val MAGIC_STATIC = "LKE1"
        const val MAGIC_VIDEO = "LKE2"
        const val VERSION_STATIC: Byte = 0x01
        const val VERSION_VIDEO: Byte = 0x02
        const val NONCE_LEN = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val AAD_PREFIX = "xianyun-media:"
        const val DEFAULT_VIDEO_CHUNK_SIZE = 64 * 1024
        private const val MAGIC_LEN = 4
        private const val STREAM_BUFFER_SIZE = 1024 * 1024
        private val LEGACY_AAD_PREFIXES: List<String> = listOf("linka-media:")
        private val RNG = SecureRandom()
    }
}
