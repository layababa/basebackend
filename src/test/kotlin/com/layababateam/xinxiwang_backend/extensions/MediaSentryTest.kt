package com.layababateam.xinxiwang_backend.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaSentryTest {
    @Test
    fun `mediaTypeFromCategory normalizes known media categories`() {
        assertEquals("video", MediaSentry.mediaTypeFromCategory("short_video"))
        assertEquals("image", MediaSentry.mediaTypeFromCategory("avatar"))
        assertEquals("image", MediaSentry.mediaTypeFromCategory("photo_album"))
        assertEquals("audio", MediaSentry.mediaTypeFromCategory("voice_audio"))
        assertEquals("file", MediaSentry.mediaTypeFromCategory("document"))
        assertEquals("file", MediaSentry.mediaTypeFromCategory(null))
    }
}
