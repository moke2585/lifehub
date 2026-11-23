package com.lifehub.app.plugins.testing

import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 单元测试使用的 `.lhplugin` 构造器。
 *
 * 只用于测试与开发校验，不进入宿主运行时。签名格式与
 * `PluginSignatureFormat` 保持一致，测试可故意构造畸形包。
 */
object PluginArchiveTestBuilder {
    const val DEFAULT_PLUGIN_ID: String = "com.example.plugin"
    const val DEFAULT_KEY_ID: String = "test-key"

    fun keyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    fun manifestJson(
        pluginId: String = DEFAULT_PLUGIN_ID,
        version: String = "1.0.0",
        minHostVersion: Int = 1,
        capabilities: String = "[]",
        dataSchemaVersion: Int = 1,
    ): String = """
        {
          "pluginId": "$pluginId",
          "name": "示例 ZIP 插件",
          "version": "$version",
          "description": "测试插件",
          "publisher": "LifeHub",
          "icon": "plugin",
          "minHostVersion": $minHostVersion,
          "pluginSdkVersion": 1,
          "capabilities": $capabilities,
          "entryPage": "home",
          "pages": [{"id":"home","title":"首页","file":"pages/home.json"}],
          "dataSchemaVersion": $dataSchemaVersion
        }
    """.trimIndent()

    fun pageJson(pageId: String = "home"): String = """
        {
          "schemaVersion": 1,
          "pageId": "$pageId",
          "title": "首页",
          "nodes": [{"type":"text","text":"内容"}]
        }
    """.trimIndent()

    fun validFiles(
        pluginId: String = DEFAULT_PLUGIN_ID,
        version: String = "1.0.0",
        capabilities: String = "[]",
    ): Map<String, ByteArray> = mapOf(
        "manifest.json" to manifestJson(pluginId, version, capabilities = capabilities).toByteArray(),
        "pages/home.json" to pageJson().toByteArray(),
    )

    /** 生成包含签名文件的完整 `.lhplugin` 字节。 */
    fun archive(
        pluginId: String = DEFAULT_PLUGIN_ID,
        version: String = "1.0.0",
        files: Map<String, ByteArray> = validFiles(pluginId, version),
        keyPair: KeyPair = keyPair(),
        keyId: String = DEFAULT_KEY_ID,
        root: String = pluginId,
    ): ByteArray {
        val signature = signature(pluginId, version, files, keyPair, keyId)
        return zip(root, files + ("manifest.sig" to signature.toByteArray()))
    }

    /** 生成签名 JSON，不写入 ZIP。 */
    fun signature(
        pluginId: String,
        version: String,
        files: Map<String, ByteArray>,
        keyPair: KeyPair,
        keyId: String = DEFAULT_KEY_ID,
    ): String {
        val digests = files.map { (path, bytes) -> path to sha256Hex(bytes) }
            .sortedBy { it.first }
        val payload = canonicalPayload(pluginId, version, digests)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(payload)
        val signature = Base64.getEncoder().encodeToString(signer.sign())

        val filesJson = digests.joinToString(separator = ",") { (path, hash) ->
            """{"path":${json(path)},"sha256":${json(hash)}}"""
        }
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        return """
            {
              "schema": 1,
              "algorithm": "ECDSA_P256_SHA256",
              "pluginId": ${json(pluginId)},
              "version": ${json(version)},
              "keyId": ${json(keyId)},
              "publicKey": ${json(publicKey)},
              "signature": ${json(signature)},
              "files": [$filesJson]
            }
        """.trimIndent()
    }

    /** 构造未签名的 ZIP，用于路径安全、重复条目等结构测试。 */
    fun zip(root: String, files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry("$root/$path"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun canonicalPayload(
        pluginId: String,
        version: String,
        digests: List<Pair<String, String>>,
    ): ByteArray = buildString {
        append("lifehub-lhplugin-v1\n")
        append(pluginId).append('\n')
        append(version).append('\n')
        digests.forEach { (path, hash) ->
            append(path).append('\t').append(hash).append('\n')
        }
    }.toByteArray(Charsets.UTF_8)

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
