package com.lifehub.app.plugin.runtime.archive

import com.lifehub.pluginapi.PluginPathRules
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 插件包中声明的单个文件摘要。 */
@Serializable
data class PluginFileDigest(
    val path: String,
    val sha256: String,
)

/** `manifest.sig` 的解析结果。 */
data class PluginSignature(
    val schema: Int,
    val algorithm: String,
    val pluginId: String,
    val version: String,
    val keyId: String,
    val publicKey: String,
    val signature: String,
    val files: List<PluginFileDigest>,
)

/** 通过签名校验的签名者信息。指纹为公钥 DER 字节的 SHA-256。 */
data class PluginSigner(
    val keyId: String,
    val publicKeyBase64: String,
    val fingerprint: String,
)

object PluginSignatureFormat {
    const val SCHEMA: Int = 1
    const val ALGORITHM: String = "ECDSA_P256_SHA256"
    const val PAYLOAD_PREFIX: String = "lifehub-lhplugin-v1"

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    @Serializable
    private data class SignatureDto(
        val schema: Int,
        val algorithm: String,
        val pluginId: String,
        val version: String,
        val keyId: String,
        val publicKey: String,
        val signature: String,
        val files: List<PluginFileDigest>,
    )

    fun parse(raw: String): Result<PluginSignature> = runCatching {
        val dto = json.decodeFromString<SignatureDto>(raw)
        require(dto.schema == SCHEMA) { "不支持的签名格式版本：${dto.schema}" }
        require(dto.algorithm == ALGORITHM) { "不支持的签名算法：${dto.algorithm}" }
        require(dto.pluginId.isNotBlank()) { "签名缺少 pluginId" }
        require(dto.version.isNotBlank()) { "签名缺少 version" }
        require(dto.keyId.isNotBlank()) { "签名缺少 keyId" }
        require(dto.publicKey.isNotBlank()) { "签名缺少 publicKey" }
        require(dto.signature.isNotBlank()) { "签名缺少 signature" }
        require(dto.files.isNotEmpty()) { "签名文件清单为空" }
        require(dto.files.size == dto.files.map { it.path }.distinct().size) {
            "签名文件清单包含重复路径"
        }
        dto.files.forEach { file ->
            require(PluginPathRules.isSafeRelativePath(file.path)) {
                "签名文件路径不安全：${file.path}"
            }
            require(file.sha256.matches(Regex("^[0-9a-f]{64}$"))) {
                "签名文件摘要格式不合法：${file.path}"
            }
        }
        PluginSignature(
            schema = dto.schema,
            algorithm = dto.algorithm,
            pluginId = dto.pluginId,
            version = dto.version,
            keyId = dto.keyId,
            publicKey = dto.publicKey,
            signature = dto.signature,
            files = dto.files,
        )
    }

    /**
     * 校验签名并返回签名者。
     *
     * [files] 必须包含除 `manifest.sig` 以外的全部包内文件。
     */
    fun verify(
        signature: PluginSignature,
        files: Map<String, ByteArray>,
    ): Result<PluginSigner> = runCatching {
        require(signature.algorithm == ALGORITHM) {
            "不支持的签名算法：${signature.algorithm}"
        }

        val declared = signature.files.associate { it.path to it.sha256 }
        require(declared.keys == files.keys) {
            val missing = declared.keys - files.keys
            val extra = files.keys - declared.keys
            buildString {
                append("签名文件清单与包内容不一致")
                if (missing.isNotEmpty()) append("；缺少：${missing.joinToString()}")
                if (extra.isNotEmpty()) append("；未签名：${extra.joinToString()}")
            }
        }
        declared.forEach { (path, expected) ->
            val actual = sha256Hex(files.getValue(path))
            require(actual == expected) { "文件摘要不匹配：$path" }
        }

        val publicKeyBytes = decodeBase64(signature.publicKey, "publicKey")
        val key = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val ecKey = key as? ECPublicKey
            ?: error("publicKey 不是 EC 公钥")
        require(ecKey.params.curve.field.fieldSize == 256) {
            "publicKey 必须是 P-256 曲线"
        }

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(canonicalPayload(signature))
        val signatureBytes = decodeBase64(signature.signature, "signature")
        require(verifier.verify(signatureBytes)) { "签名校验失败" }

        PluginSigner(
            keyId = signature.keyId,
            publicKeyBase64 = signature.publicKey,
            fingerprint = sha256Hex(publicKeyBytes),
        )
    }

    fun canonicalPayload(signature: PluginSignature): ByteArray = buildString {
        append(PAYLOAD_PREFIX).append('\n')
        append(signature.pluginId).append('\n')
        append(signature.version).append('\n')
        signature.files
            .sortedBy { it.path }
            .forEach { file ->
                append(file.path).append('\t').append(file.sha256).append('\n')
            }
    }.toByteArray(Charsets.UTF_8)

    fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun decodeBase64(value: String, field: String): ByteArray =
        runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { error("$field 不是合法的 Base64") }
}
