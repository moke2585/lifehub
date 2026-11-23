package com.lifehub.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val password = "correct-password".toCharArray()
    private val plaintext = """{"hello":"世界"}""".toByteArray(Charsets.UTF_8)

    @Test
    fun encryptsAndDecryptsRoundTrip() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)

        assertFalse(encrypted.contentEquals(plaintext))
        assertArrayEquals(plaintext, BackupCrypto.decrypt(encrypted, password))
    }

    @Test
    fun wrongPasswordFails() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)

        val error = runCatching {
            BackupCrypto.decrypt(encrypted, "wrong-password".toCharArray())
        }.exceptionOrNull()

        assertTrue(error is BackupException)
    }

    @Test
    fun tamperedContainerFails() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        val error = runCatching { BackupCrypto.decrypt(encrypted, password) }.exceptionOrNull()

        assertTrue(error is BackupException)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortPasswordOnExport() {
        BackupCrypto.encrypt(plaintext, "short".toCharArray())
    }
}
