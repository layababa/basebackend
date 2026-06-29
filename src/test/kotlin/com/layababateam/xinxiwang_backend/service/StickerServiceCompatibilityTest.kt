package com.layababateam.xinxiwang_backend.service

import com.aliyun.oss.OSS
import com.layababateam.xinxiwang_backend.model.Sticker
import com.layababateam.xinxiwang_backend.repository.StickerRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class StickerServiceCompatibilityTest {
    @Test
    fun `saving an existing favorite sticker returns existing row without duplicate save`() {
        val existing = Sticker(
            id = "s1",
            userId = "u1",
            originalUrl = "https://cdn.example.com/stickers/a.webp",
            isFavorite = true,
        )
        val service = StickerService(
            stickerRepository = stickerRepository(existing),
            ossService = OssService(ossProxy(), ossProxy(), ossProxy(), "rentmsg", "debug-logs/"),
            endpointResolver = MediaEndpointResolver(
                fallbackEndpoint = "https://cdn.example.com",
                directEndpoint = "",
                serverNodeRepository = emptyServerNodeRepository(),
            ),
        )

        assertEquals(existing, service.saveFavoriteSticker("u1", existing.originalUrl))
    }

    private fun stickerRepository(existing: Sticker): StickerRepository {
        return Proxy.newProxyInstance(
            StickerRepository::class.java.classLoader,
            arrayOf(StickerRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findByUserIdAndOriginalUrl" -> existing.takeIf {
                    args?.getOrNull(0) == existing.userId && args.getOrNull(1) == existing.originalUrl
                }
                "save" -> error("duplicate save should not be called")
                else -> throw UnsupportedOperationException(method.name)
            }
        } as StickerRepository
    }

    private fun ossProxy(): OSS {
        return Proxy.newProxyInstance(OSS::class.java.classLoader, arrayOf(OSS::class.java)) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as OSS
    }
}
