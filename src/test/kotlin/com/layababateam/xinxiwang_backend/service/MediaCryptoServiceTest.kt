package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A2 / A6 护栏：LKE1 加解密往返、AAD 绑定（错 mediaId 解密失败）、legacy AAD 回退、header 解析边界。
 */
class MediaCryptoServiceTest {

    // 两把 32 字节 base64 master key（local 环境直接装配，不走 ephemeral 兜底）
    private val keyA = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=" // "0123...def"
    private val keyB = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=" // 另一把

    private fun registry(): MediaKeyRegistry {
        // keyB 第二把为校验 keyId 选择；current = k1
        val cfg = "k1:$keyA,k2:$keyB"
        return MediaKeyRegistry(
            keysConfig = cfg,
            currentId = "k1",
            appEnvironment = "local",
        ).also { it.init() }
    }

    @Test
    fun `LKE1 encrypt then decrypt roundtrips plaintext`() {
        val svc = MediaCryptoService(registry())
        val plain = "hello media payload 你好".toByteArray(Charsets.UTF_8)
        val mediaId = "media-123"

        val blob = svc.encryptStatic(plain, mediaId)
        val out = svc.decryptStatic(blob, mediaId)

        assertEquals(String(plain, Charsets.UTF_8), String(out, Charsets.UTF_8))
    }

    @Test
    fun `header parses LKE1 magic version keyId and nonce`() {
        val svc = MediaCryptoService(registry())
        val blob = svc.encryptStatic("x".toByteArray(), "media-1", keyId = "k2")
        val header = svc.parseHeader(blob)

        assertEquals(MediaCryptoService.MAGIC_STATIC, header.magic)
        assertEquals(MediaCryptoService.VERSION_STATIC.toInt(), header.version)
        assertEquals("k2", header.keyId)
        assertEquals(MediaCryptoService.NONCE_LEN, header.nonce.size)
    }

    @Test
    fun `AAD binding - decrypting with wrong mediaId fails authentication`() {
        val svc = MediaCryptoService(registry())
        val blob = svc.encryptStatic("secret".toByteArray(), "media-correct")

        // 错 mediaId：current AAD 不匹配，legacy 回退也不匹配 → 最终重试仍抛 AEADBadTag
        assertFailsWith<javax.crypto.AEADBadTagException> {
            svc.decryptStatic(blob, "media-WRONG")
        }
    }

    @Test
    fun `tampering keyId byte makes decryption fail (header keyId selects key)`() {
        val svc = MediaCryptoService(registry())
        val blob = svc.encryptStatic("data".toByteArray(), "media-x", keyId = "k1")
        // 把 keyId 'k1' 篡改为 'k2'（同长度），选错 key → 解密失败
        val tampered = blob.copyOf()
        val keyIdOffset = 4 + 1 + 1 // magic(4)+version(1)+keyIdLen(1)
        tampered[keyIdOffset + 1] = '2'.code.toByte() // k1 -> k2

        assertFailsWith<javax.crypto.AEADBadTagException> {
            svc.decryptStatic(tampered, "media-x")
        }
    }

    @Test
    fun `legacy AAD prefix fallback - blob built with linka-media prefix still decrypts`() {
        val reg = registry()
        val svc = MediaCryptoService(reg)
        val mediaId = "legacy-media-9"

        // 手工用 legacy AAD "linka-media:" 加密，模拟命名空间重命名前的旧密文，
        // 断言 decryptStatic 通过 LEGACY_AAD_PREFIXES 回退成功解密。
        val key = reg.keyById("k1")!!
        val nonce = ByteArray(MediaCryptoService.NONCE_LEN).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(MediaCryptoService.GCM_TAG_BITS, nonce),
        )
        cipher.updateAAD(("linka-media:$mediaId").toByteArray(Charsets.UTF_8))
        val plain = "legacy bytes".toByteArray()
        val ct = cipher.doFinal(plain)

        val keyIdBytes = "k1".toByteArray(Charsets.US_ASCII)
        val out = java.io.ByteArrayOutputStream()
        out.write("LKE1".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x01))
        out.write(byteArrayOf(keyIdBytes.size.toByte()))
        out.write(keyIdBytes)
        out.write(nonce)
        out.write(ct)
        val blob = out.toByteArray()

        val decrypted = svc.decryptStatic(blob, mediaId)
        assertEquals("legacy bytes", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `parseHeader rejects truncated blob`() {
        val svc = MediaCryptoService(registry())
        assertFailsWith<IllegalArgumentException> {
            svc.parseHeader(byteArrayOf(0x4C, 0x4B, 0x45)) // "LKE" 太短
        }
    }

    @Test
    fun `MediaKeyRegistry fail-fast in production when key missing`() {
        // M4 护栏：production 环境缺密钥（占位符）→ init() 抛 IllegalStateException
        val reg = MediaKeyRegistry(
            keysConfig = "k1:CHANGE_ME_BASE64_32_BYTES_KEY",
            currentId = "k1",
            appEnvironment = "production",
        )
        val ex = assertFailsWith<IllegalStateException> { reg.init() }
        assertTrue(ex.message!!.contains("拒绝使用临时随机密钥"))
    }

    @Test
    fun `MediaKeyRegistry local env falls back to ephemeral key when missing`() {
        // local 环境占位符 → 兜底成功（不抛），currentKey 可用
        val reg = MediaKeyRegistry(
            keysConfig = "k1:CHANGE_ME_BASE64_32_BYTES_KEY",
            currentId = "k1",
            appEnvironment = "local",
        )
        reg.init()
        assertEquals(32, reg.currentKey().size)
    }
}
