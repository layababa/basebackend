package com.layababateam.xinxiwang_backend.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class AdminJwtConfig(
    @Value("\${xinxiwang.admin.jwt-secret}") private val secret: String,
    @Value("\${xinxiwang.admin.jwt-expiration}") private val expiration: Long,
    @Value("\${xinxiwang.admin.jwt-refresh-expiration}") private val refreshExpiration: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateAccessToken(adminId: String, username: String, role: String, tokenVersion: Long): String {
        return Jwts.builder()
            .subject(adminId)
            .claim("username", username)
            .claim("role", role)
            .claim("type", "access")
            .claim("tokenVersion", tokenVersion)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(adminId: String, tokenVersion: Long): String {
        return Jwts.builder()
            .subject(adminId)
            .claim("type", "refresh")
            .claim("tokenVersion", tokenVersion)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(key)
            .compact()
    }

    fun generateTempToken(adminId: String, tokenVersion: Long): String {
        return Jwts.builder()
            .subject(adminId)
            .claim("type", "2fa_temp")
            .claim("tokenVersion", tokenVersion)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + TEMP_TOKEN_EXPIRATION_MS))
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        } catch (_: Exception) {
            null
        }
    }

    fun getAdminId(claims: Claims): String = claims.subject

    fun getRole(claims: Claims): String = claims["role"] as? String ?: "MODERATOR"

    fun getUsername(claims: Claims): String = claims["username"] as? String ?: ""

    fun getTokenType(claims: Claims): String = claims["type"] as? String ?: ""

    fun getTokenVersion(claims: Claims): Long? {
        val value = claims["tokenVersion"] ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private companion object {
        private const val TEMP_TOKEN_EXPIRATION_MS = 300_000L
    }
}
