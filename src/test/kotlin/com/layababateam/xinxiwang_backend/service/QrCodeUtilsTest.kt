package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D2/D3 既有设计护栏：group/meeting 二维码加解密往返、7 天过期判定、损坏输入返回 null。
 */
class QrCodeUtilsTest {

    private val utils = QrCodeUtils(aesKey = "test-qr-aes-key-32-bytes-long!!!")

    @Test
    fun `group QR encrypt then decrypt roundtrips fields`() {
        val enc = utils.encryptGroupQr("group-1", "user-9", 1718764800000L)
        val payload = utils.decryptGroupQr(enc)
        assertEquals("group-1", payload?.groupId)
        assertEquals("user-9", payload?.userId)
        assertEquals(1718764800000L, payload?.timestamp)
    }

    @Test
    fun `meeting invite encrypt then decrypt roundtrips fields including moderator flag`() {
        val enc = utils.encryptMeetingInvite("meeting-7", "inviter-3", moderatorInvite = true)
        val payload = utils.decryptMeetingInvite(enc)
        assertEquals("meeting-7", payload?.meetingId)
        assertEquals("inviter-3", payload?.inviterId)
        assertEquals(true, payload?.moderatorInvite)
    }

    @Test
    fun `meeting invite preserves non-moderator flag`() {
        val enc = utils.encryptMeetingInvite("m", "i", moderatorInvite = false)
        assertEquals(false, utils.decryptMeetingInvite(enc)?.moderatorInvite)
    }

    @Test
    fun `decryptGroupQr returns null for corrupted input`() {
        assertNull(utils.decryptGroupQr("not-valid-base64url-@@@"))
        assertNull(utils.decryptGroupQr(""))
    }

    @Test
    fun `decryptMeetingInvite returns null when prefix mismatches`() {
        // 用 group 密文喂 meeting 解密：解出的明文不以 "meetingInvite" 开头 → null
        val groupEnc = utils.encryptGroupQr("g", "u", 123L)
        assertNull(utils.decryptMeetingInvite(groupEnc))
    }

    @Test
    fun `isExpired true beyond 7 days false within`() {
        val now = System.currentTimeMillis()
        val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000
        val sixDaysAgo = now - 6L * 24 * 60 * 60 * 1000
        assertTrue(utils.isExpired(eightDaysAgo))
        assertFalse(utils.isExpired(sixDaysAgo))
    }

    @Test
    fun `isExpired boundary just under 7 days is not expired`() {
        val justUnder = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000 - 60_000L)
        assertFalse(utils.isExpired(justUnder))
    }
}
