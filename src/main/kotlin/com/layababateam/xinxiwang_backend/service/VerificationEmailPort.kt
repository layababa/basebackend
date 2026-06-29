package com.layababateam.xinxiwang_backend.service

interface VerificationEmailPort {
    fun send(to: String, subject: String, body: String)
}
