package com.lifehub.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份容器加密：PBKDF2-HMAC-SHA256 派生密钥 + AES-256-GCM。
 * 口令不落盘，不使用设备 Keystore，换机后仍可用同一口令恢复。
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('L'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'E'.code.toByte(), 'H'.code.toByte(), 'U'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private const val MAX_HEADER_CHUNK = 64

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= BackupLimits.MIN_PASSWORD_LENGTH) {
            "口令至少需要 ${BackupLimits.MIN_PASSWORD_LENGTH} 个字符"
        }
        val random = SecureRandom()
        val salt = ByteArray(BackupLimits.SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(BackupLimits.IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(BackupLimits.GCM_TAG_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plain)

        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        data.write(MAGIC)
        data.writeInt(BackupLimits.CONTAINER_VERSION)
        data.writeInt(salt.size)
        data.write(salt)
        data.writeInt(iv.size)
        data.write(iv)
        data.writeInt(ciphertext.size)
        data.write(ciphertext)
        data.flush()
        return output.toByteArray()
    }

    fun decrypt(container: ByteArray, password: CharArray): ByteArray {
        if (container.size > BackupLimits.MAX_ARCHIVE_BYTES + 4096) {
            throw BackupException("备份文件超过大小上限")
        }
        try {
            DataInputStream(ByteArrayInputStream(container)).use { input ->
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) {
                    throw BackupException("不是 LifeHub 备份档案")
                }
                val version = input.readInt()
                if (version != BackupLimits.CONTAINER_VERSION) {
                    throw BackupException("不支持的备份容器版本：$version")
                }
                val salt = readChunk(input)
                val iv = readChunk(input)
                val ciphertextLength = input.readInt()
                if (ciphertextLength <= 0 || ciphertextLength > BackupLimits.MAX_ARCHIVE_BYTES + 4096) {
                    throw BackupException("备份档案长度不合法")
                }
                val ciphertext = ByteArray(ciphertextLength)
                input.readFully(ciphertext)
                if (input.available() > 0) {
                    throw BackupException("备份档案包含多余数据")
                }

                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(BackupLimits.GCM_TAG_BITS, iv),
                )
                return cipher.doFinal(ciphertext)
            }
        } catch (error: AEADBadTagException) {
            throw BackupException("口令错误或备份档案已损坏", error)
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException("解密备份失败：${error.message ?: "未知错误"}", error)
        }
    }

    private fun readChunk(input: DataInputStream): ByteArray {
        val length = input.readInt()
        if (length !in 1..MAX_HEADER_CHUNK) {
            throw BackupException("备份档案头损坏")
        }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password,
            salt,
            BackupLimits.PBKDF2_ITERATIONS,
            BackupLimits.KEY_BITS,
        )
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
