package com.layababateam.xinxiwang_backend.service

interface AsrPort {
    fun transcribe(audioUrl: String, format: String = DEFAULT_FORMAT): String

    companion object {
        const val DEFAULT_FORMAT = "m4a"
    }
}
