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
    @Value("\${rentmsg.admin.jwt-secret}") private val secret: String,
    @Value("\${rentmsg.admin.jwt-expiration}") private val expiration: Long,
    @Value("\${rentmsg.admin.jwt-refresh-expiration}") private val refreshExpiration: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateAccessToken(adminId: String, username: String, role: String): String {
        return Jwts.builder()
            .subject(adminId)
            .claim("username", username)
            .claim("role", role)
            .claim("type", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(adminId: String): String {
        return Jwts.builder()
            .subject(adminId)
            .claim("type", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(key)
            .compact()
    }

    fun generateTempToken(adminId: String): String {
        return Jwts.builder()
            .subject(adminId)
            .claim("type", "2fa_temp")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 300000)) // 5 min
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        } catch (e: Exception) {
            null
        }
    }

    fun getAdminId(claims: Claims): String = claims.subject
    fun getRole(claims: Claims): String = claims["role"] as? String ?: "MODERATOR"
    fun getUsername(claims: Claims): String = claims["username"] as? String ?: ""
    fun getTokenType(claims: Claims): String = claims["type"] as? String ?: ""
}
