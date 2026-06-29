package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.AdminJwtConfig
import com.layababateam.xinxiwang_backend.model.Admin
import com.layababateam.xinxiwang_backend.repository.AdminRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import com.layababateam.xinxiwang_backend.config.AesPasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class AdminAuthService(
    private val adminRepository: AdminRepository,
    private val jwtConfig: AdminJwtConfig,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val aesPasswordEncoder: AesPasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(AdminAuthService::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun seedDefaultAdmin() {
        val defaultPwd = "Qwer1234!@#$"
        val existingAdmin = adminRepository.findByUsername("admin")
        if (existingAdmin != null) {
            val updated = existingAdmin.copy(
                passwordHash = aesPasswordEncoder.encrypt(defaultPwd),
                updatedAt = System.currentTimeMillis()
            )
            adminRepository.save(updated)
            logger.info("Admin 'admin' password has been reset to default.")
        } else {
            val defaultAdmin = Admin(
                username = "admin",
                passwordHash = aesPasswordEncoder.encrypt(defaultPwd),
                role = "SUPER_ADMIN",
                mustChangePassword = true
            )
            adminRepository.save(defaultAdmin)
            logger.warn("====================================================")
            logger.warn("Default SUPER_ADMIN created.")
            logger.warn("Username: admin")
            logger.warn("IMPORTANT: Change this password immediately after first login!")
            logger.warn("====================================================")
        }
    }

    data class LoginResult(
        val success: Boolean,
        val requiresTwoFactor: Boolean = false,
        val tempToken: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val admin: AdminProfile? = null,
        val mustChangePassword: Boolean = false,
        val error: String? = null
    )

    data class AdminProfile(
        val id: String,
        val username: String,
        val role: String,
        val totpEnabled: Boolean,
        val lastLoginAt: Long?,
        val createdAt: Long
    )

    fun login(username: String, password: String, ip: String?): LoginResult {
        val admin = adminRepository.findByUsername(username)
            ?: return LoginResult(success = false, error = "Invalid credentials")

        if (!admin.isActive) {
            return LoginResult(success = false, error = "Account is disabled")
        }

        if (!aesPasswordEncoder.matches(password, admin.passwordHash)) {
            return LoginResult(success = false, error = "Invalid credentials")
        }

        // Update last login
        val updatedAdmin = admin.copy(
            lastLoginAt = System.currentTimeMillis(),
            lastLoginIp = ip,
            updatedAt = System.currentTimeMillis()
        )
        adminRepository.save(updatedAdmin)

        if (admin.totpEnabled && admin.totpSecret != null) {
            val tempToken = jwtConfig.generateTempToken(admin.id!!)
            return LoginResult(
                success = true,
                requiresTwoFactor = true,
                tempToken = tempToken
            )
        }

        val accessToken = jwtConfig.generateAccessToken(admin.id!!, admin.username, admin.role)
        val refreshToken = jwtConfig.generateRefreshToken(admin.id)
        return LoginResult(
            success = true,
            accessToken = accessToken,
            refreshToken = refreshToken,
            mustChangePassword = admin.mustChangePassword,
            admin = AdminProfile(
                id = admin.id,
                username = admin.username,
                role = admin.role,
                totpEnabled = admin.totpEnabled,
                lastLoginAt = updatedAdmin.lastLoginAt,
                createdAt = admin.createdAt
            )
        )
    }

    fun verify2fa(tempToken: String, totpCode: String): LoginResult {
        val claims = jwtConfig.validateToken(tempToken)
            ?: return LoginResult(success = false, error = "Invalid or expired temp token")

        if (jwtConfig.getTokenType(claims) != "2fa_temp") {
            return LoginResult(success = false, error = "Invalid token type")
        }

        val adminId = jwtConfig.getAdminId(claims)
        val admin = adminRepository.findById(adminId).orElse(null)
            ?: return LoginResult(success = false, error = "Admin not found")

        // Simple TOTP verification (6-digit code verification)
        if (!verifyTotpCode(admin.totpSecret!!, totpCode)) {
            return LoginResult(success = false, error = "Invalid 2FA code")
        }

        val accessToken = jwtConfig.generateAccessToken(admin.id!!, admin.username, admin.role)
        val refreshToken = jwtConfig.generateRefreshToken(admin.id)
        return LoginResult(
            success = true,
            accessToken = accessToken,
            refreshToken = refreshToken,
            mustChangePassword = admin.mustChangePassword,
            admin = AdminProfile(
                id = admin.id,
                username = admin.username,
                role = admin.role,
                totpEnabled = admin.totpEnabled,
                lastLoginAt = admin.lastLoginAt,
                createdAt = admin.createdAt
            )
        )
    }

    fun refreshToken(refreshTokenStr: String): LoginResult {
        val claims = jwtConfig.validateToken(refreshTokenStr)
            ?: return LoginResult(success = false, error = "Invalid or expired refresh token")

        if (jwtConfig.getTokenType(claims) != "refresh") {
            return LoginResult(success = false, error = "Invalid token type")
        }

        val adminId = jwtConfig.getAdminId(claims)
        val admin = adminRepository.findById(adminId).orElse(null)
            ?: return LoginResult(success = false, error = "Admin not found")

        if (!admin.isActive) {
            return LoginResult(success = false, error = "Account is disabled")
        }

        val accessToken = jwtConfig.generateAccessToken(admin.id!!, admin.username, admin.role)
        val newRefreshToken = jwtConfig.generateRefreshToken(admin.id)
        return LoginResult(
            success = true,
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            admin = AdminProfile(
                id = admin.id,
                username = admin.username,
                role = admin.role,
                totpEnabled = admin.totpEnabled,
                lastLoginAt = admin.lastLoginAt,
                createdAt = admin.createdAt
            )
        )
    }

    fun changePassword(adminId: String, oldPassword: String, newPassword: String): Boolean {
        val admin = adminRepository.findById(adminId).orElse(null) ?: return false
        if (!aesPasswordEncoder.matches(oldPassword, admin.passwordHash)) return false
        val updated = admin.copy(
            passwordHash = aesPasswordEncoder.encrypt(newPassword),
            mustChangePassword = false,
            updatedAt = System.currentTimeMillis()
        )
        adminRepository.save(updated)
        return true
    }

    fun resetPassword(adminId: String, newPassword: String): Boolean {
        val admin = adminRepository.findById(adminId).orElse(null) ?: return false
        val updated = admin.copy(
            passwordHash = aesPasswordEncoder.encrypt(newPassword),
            mustChangePassword = true,
            updatedAt = System.currentTimeMillis()
        )
        adminRepository.save(updated)
        return true
    }

    fun getProfile(adminId: String): AdminProfile? {
        val admin = adminRepository.findById(adminId).orElse(null) ?: return null
        return AdminProfile(
            id = admin.id!!,
            username = admin.username,
            role = admin.role,
            totpEnabled = admin.totpEnabled,
            lastLoginAt = admin.lastLoginAt,
            createdAt = admin.createdAt
        )
    }

    fun createAdmin(username: String, password: String, role: String): Admin? {
        if (adminRepository.existsByUsername(username)) return null
        val admin = Admin(
            username = username,
            passwordHash = aesPasswordEncoder.encrypt(password),
            role = role,
            mustChangePassword = true
        )
        return adminRepository.save(admin)
    }

    fun getAdminRole(adminId: String): String {
        return adminRepository.findById(adminId).orElse(null)?.role ?: "MODERATOR"
    }

    // RFC 6238 compliant TOTP implementation using standard library
    private fun verifyTotpCode(secret: String, code: String): Boolean {
        if (code.length != 6 || !code.all { it.isDigit() }) return false
        val timeStep = System.currentTimeMillis() / 1000 / 30
        for (i in -1..1) {
            if (generateTotp(secret, timeStep + i) == code) return true
        }
        return false
    }

    private fun generateTotp(secret: String, timeStep: Long): String {
        val data = ByteArray(8)
        var value = timeStep
        for (i in 7 downTo 0) {
            data[i] = (value and 0xFF).toByte()
            value = value shr 8
        }
        val decodedKey = base32Decode(secret)
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(decodedKey, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val truncatedHash = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val otp = truncatedHash % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    private fun base32Decode(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleanInput = base32.uppercase().replace("=", "")
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (c in cleanInput) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add((buffer shr (bitsLeft - 8) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }
}
