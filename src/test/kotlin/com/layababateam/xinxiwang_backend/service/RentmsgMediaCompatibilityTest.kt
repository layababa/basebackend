package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RentmsgMediaCompatibilityTest {
    @Test
    fun `client log config is enabled only for supported client versions`() {
        val gate = ClientVersionGate()

        assertFalse(gate.isEligible("1.0.6+75", supportsClientLogConfig = true))
        assertFalse(gate.isEligible("1.0.6+76", supportsClientLogConfig = true))
        assertFalse(gate.isEligible("1.0.7+75", supportsClientLogConfig = true))
        assertTrue(gate.isEligible("1.0.7+76", supportsClientLogConfig = true))
        assertTrue(gate.isEligible("1.1.0+76", supportsClientLogConfig = true))

        assertFalse(gate.isEligible("1.0.7+76", supportsClientLogConfig = false))
        assertFalse(gate.isEligible("1.0.7", supportsClientLogConfig = true))
        assertFalse(gate.isEligible("bad+76", supportsClientLogConfig = true))
        assertFalse(gate.isEligible(null, supportsClientLogConfig = true))
    }

    @Test
    fun `retired media endpoints are canonicalized and encrypted paths are migrated`() {
        assertEquals(
            MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT,
            MediaEndpointPolicy.canonicalizePublicEndpoint("https://oss.rgzzsb.cn/"),
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/files/a.txt?x=1#preview",
            MediaEndpointPolicy.rewriteDeprecatedMediaUrl(
                "https://rentmsg-media.s3-accelerate.amazonaws.com/files/a.txt?x=1#preview",
            ),
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/thumbnails/images/m1.jpg?x=1",
            MediaEndpointPolicy.rewriteDeprecatedMediaUrl(
                "https://s3.12da.yufengep.com/encrypted/thumbnails/images/m1.bin?x=1",
            ),
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/audio/a1.m4a",
            MediaEndpointPolicy.rewriteDeprecatedMediaUrl(
                "https://s3.12da.rgzzsb.cn/encrypted/audio/a1.bin",
            ),
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/images/i1.jpg",
            MediaEndpointPolicy.rewriteDeprecatedMediaUrl(
                "https://s3.12da.rgzzsb.cn/encrypted/images/i1.bin",
                plainExtension = "jpeg",
            ),
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/encrypted/audio/a1.bin",
            MediaEndpointPolicy.rewriteDeprecatedCipherUrl(
                "https://s3.12da.rgzzsb.cn/encrypted/audio/a1.bin",
            ),
        )
    }

    @Test
    fun `legacy sticker urls are rewritten without touching unrelated urls`() {
        assertEquals(
            "https://12da.yufengep.com/appserver/api/media/a/b.gif",
            LegacyStickerUrlRewriter.rewrite("https://12da.rgzzsb.cn/appserver/api/media/a/b.gif"),
        )
        assertEquals(
            "https://rentmsg-hk.oss-accelerate.aliyuncs.com/stickers/a.gif?x=1",
            LegacyStickerUrlRewriter.rewrite("https://s3.12da.rgzzsb.cn/stickers/a.gif?x=1"),
        )
        assertEquals(
            "https://evil.example.com/?u=s3.12da.rgzzsb.cn/stickers/a.gif",
            LegacyStickerUrlRewriter.rewrite("https://evil.example.com/?u=s3.12da.rgzzsb.cn/stickers/a.gif"),
        )
        assertEquals("not a url", LegacyStickerUrlRewriter.rewrite("not a url"))
    }

    @Test
    fun `apk download urls are resolved to the current public media endpoint`() {
        val endpointResolver = MediaEndpointResolver(
            fallbackEndpoint = "https://download.example.com/",
            directEndpoint = "",
            serverNodeRepository = emptyServerNodeRepository(),
        )
        val resolver = ApkDownloadUrlResolver(endpointResolver, bucket = "rentmsg-hk")

        assertEquals(
            "https://download.example.com/releases/rentmsg-1.2.3.apk?version=1#install",
            resolver.resolve("https://oss-cn-hongkong.aliyuncs.com/releases/rentmsg-1.2.3.apk?version=1#install"),
        )
        assertEquals(
            "https://download.example.com/releases/rentmsg.apk",
            resolver.resolve("https://oss-cn-hongkong.aliyuncs.com/rentmsg-hk/releases/rentmsg.apk"),
        )

        val nonApkUrl = "https://oss-cn-hongkong.aliyuncs.com/releases/rentmsg-1.2.3.zip"
        assertEquals(nonApkUrl, resolver.resolve(nonApkUrl))
        assertEquals("not a url.apk", resolver.resolve("not a url.apk"))
        assertEquals(null, resolver.resolveNullable(null))
    }
}
