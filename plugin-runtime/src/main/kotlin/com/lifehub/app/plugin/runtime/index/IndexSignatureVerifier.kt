package com.lifehub.app.plugin.runtime.index

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * `registry.sig`：对 `registry.json` 原始字节的 SHA-256 做 ECDSA P-256 签名。
 *
 * 载荷：`lifehub-index-v1\n<registry.json 的 64 位小写 SHA-256>`。
 */
@Serializable
data class IndexSignature(
    val schema: Int,
    val algorithm: String,
    val keyId: String,
    val registrySha256: String,
    val signature: String,
    val signedAt: String? = null,
)

object IndexSignatureVerifier {
    const val SCHEMA: Int = 1
    const val ALGORITHM: String = "ECDSA_P256_SHA256"
    const val PAYLOAD_PREFIX: String = "lifehub-index-v1"

    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val KEY_ID = Regex("^[A-Za-z0-9._-]+$")
    private val PEM_BLOCK = Regex(
        "-----BEGIN PUBLIC KEY-----([\\s\\S]*?)-----END PUBLIC KEY-----",
    )
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(raw: String): Result<IndexSignature> = runCatching {
        val signature = json.decodeFromString<IndexSignature>(raw)
        require(signature.schema == SCHEMA) { "不支持的签名格式版本：${signature.schema}" }
        require(signature.algorithm == ALGORITHM) { "不支持的签名算法：${signature.algorithm}" }
        require(KEY_ID.matches(signature.keyId)) { "keyId 格式无效" }
        require(SHA256.matches(signature.registrySha256)) { "registrySha256 格式无效" }
        require(signature.signature.isNotBlank()) { "签名值为空" }
        signature
    }

    fun payload(registrySha256: String): ByteArray =
        "$PAYLOAD_PREFIX\n$registrySha256".toByteArray(Charsets.UTF_8)

    fun verify(
        registryBytes: ByteArray,
        signature: IndexSignature,
        publicKeyPem: String,
    ): Result<Unit> = runCatching {
        require(signature.schema == SCHEMA) { "不支持的签名格式版本：${signature.schema}" }
        require(signature.algorithm == ALGORITHM) { "不支持的签名算法：${signature.algorithm}" }
        require(KEY_ID.matches(signature.keyId)) { "keyId 格式无效" }
        require(SHA256.matches(signature.registrySha256)) { "registrySha256 格式无效" }

        val actual = sha256Hex(registryBytes)
        require(actual == signature.registrySha256) { "registry.json 摘要与签名不一致" }

        val key = parsePublicKey(publicKeyPem)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(payload(signature.registrySha256))
        val signatureBytes = runCatching { Base64.getDecoder().decode(signature.signature) }
            .getOrElse { error("signature 不是合法的 Base64") }
        require(verifier.verify(signatureBytes)) { "索引签名校验失败" }
    }

    fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun parsePublicKey(pem: String): ECPublicKey {
        val match = PEM_BLOCK.find(pem)
            ?: error("索引公钥必须是 PEM 格式")
        val base64 = match.groupValues[1].replace(Regex("\\s"), "")
        val bytes = runCatching { Base64.getDecoder().decode(base64) }
            .getOrElse { error("索引公钥不是合法的 Base64") }
        val key = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(bytes)) as? ECPublicKey
            ?: error("索引公钥不是 EC 公钥")
        require(key.params.curve.field.fieldSize == 256) {
            "索引公钥必须是 P-256 曲线"
        }
        return key
    }
}
