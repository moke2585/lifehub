package com.lifehub.app.plugin.runtime.archive

import com.lifehub.pluginapi.PluginManifestParser
import com.lifehub.pluginapi.PluginPathRules
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

sealed interface PluginArchiveResult {
    data class Success(val content: PluginArchiveContent) : PluginArchiveResult

    data class Failure(val message: String) : PluginArchiveResult
}

/**
 * 通过结构与签名校验的 `.lhplugin` 内容。
 *
 * [files] 使用相对插件根目录的路径，包含 `manifest.json` 与页面/资源文件，
 * 不包含 `manifest.sig`。
 */
data class PluginArchiveContent(
    val pluginId: String,
    val files: Map<String, ByteArray>,
    val signatureBytes: ByteArray,
    val signature: PluginSignature,
    val signer: PluginSigner,
)

/**
 * `.lhplugin` v1 读取器：只读取、校验并返回内存内容，不写磁盘。
 *
 * 校验顺序：容器限额 → 路径安全 → 重复/可执行条目 → manifest 与签名一致性。
 */
object PluginArchiveReader {
    const val MAX_ARCHIVE_BYTES: Int = 16 * 1024 * 1024
    const val MAX_ENTRY_BYTES: Int = 8 * 1024 * 1024
    const val MAX_TOTAL_BYTES: Int = 16 * 1024 * 1024
    const val MAX_ENTRIES: Int = 256
    const val MAX_MANIFEST_BYTES: Int = 64 * 1024
    const val MAX_SIGNATURE_BYTES: Int = 64 * 1024

    private const val MANIFEST_FILE = "manifest.json"
    private const val SIGNATURE_FILE = "manifest.sig"

    private val forbiddenExtensions = setOf(
        "dex", "jar", "class", "so", "js", "mjs", "cjs", "wasm", "apk", "aar",
    )

    fun read(bytes: ByteArray): PluginArchiveResult {
        if (bytes.size > MAX_ARCHIVE_BYTES) {
            return failure("插件包 ${bytes.size} 字节超过上限 $MAX_ARCHIVE_BYTES")
        }

        val entries = linkedMapOf<String, ByteArray>()
        var rootSegment: String? = null
        var entryCount = 0
        var totalBytes = 0

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        return failure("插件包条目数超过上限 $MAX_ENTRIES")
                    }

                    val rawName = entry.name
                    if (!PluginPathRules.isSafeRelativePath(rawName)) {
                        return failure("插件包包含不安全路径：$rawName")
                    }
                    if (entry.isDirectory || rawName.endsWith('/')) continue

                    val segments = rawName.split('/')
                    if (segments.size < 2) {
                        return failure("插件包必须使用单一插件目录：$rawName")
                    }
                    val root = segments.first()
                    if (rootSegment == null) {
                        rootSegment = root
                    } else if (rootSegment != root) {
                        return failure("插件包包含多个顶层目录：$rootSegment 与 $root")
                    }

                    val relativePath = segments.drop(1).joinToString("/")
                    if (entries.containsKey(relativePath)) {
                        return failure("插件包包含重复条目：$relativePath")
                    }
                    if (isExecutable(relativePath)) {
                        return failure("v1 插件包不允许包含可执行文件：$relativePath")
                    }

                    val perEntryLimit = when (relativePath) {
                        MANIFEST_FILE -> MAX_MANIFEST_BYTES
                        SIGNATURE_FILE -> MAX_SIGNATURE_BYTES
                        else -> MAX_ENTRY_BYTES
                    }
                    if (entry.size > perEntryLimit) {
                        return failure("条目 $relativePath 超过 $perEntryLimit 字节上限")
                    }
                    val remaining = MAX_TOTAL_BYTES - totalBytes
                    if (remaining <= 0) {
                        return failure("插件包解压后超过 $MAX_TOTAL_BYTES 字节上限")
                    }
                    val data = readBounded(zip, minOf(perEntryLimit, remaining))
                        ?: return failure("条目 $relativePath 超过解压大小上限")
                    totalBytes += data.size
                    entries[relativePath] = data
                }
            }
        } catch (error: Exception) {
            return failure("插件包无法解析：${error.message ?: "未知错误"}")
        }

        val root = rootSegment ?: return failure("插件包为空")
        val manifestBytes = entries[MANIFEST_FILE]
            ?: return failure("插件包缺少 $MANIFEST_FILE")
        val signatureBytes = entries[SIGNATURE_FILE]
            ?: return failure("插件包缺少 $SIGNATURE_FILE")

        val manifest = PluginManifestParser.parse(
            String(manifestBytes, Charsets.UTF_8),
        ).getOrElse { error ->
            return failure("manifest 无法解析：${error.message ?: "未知错误"}")
        }
        if (manifest.pluginId != root) {
            return failure("顶层目录 $root 与 pluginId ${manifest.pluginId} 不一致")
        }

        val signature = PluginSignatureFormat.parse(
            String(signatureBytes, Charsets.UTF_8),
        ).getOrElse { error ->
            return failure("manifest.sig 无法解析：${error.message ?: "未知错误"}")
        }
        if (signature.pluginId != manifest.pluginId) {
            return failure("签名 pluginId 与 manifest 不一致")
        }
        if (signature.version != manifest.version) {
            return failure("签名 version 与 manifest 不一致")
        }

        val signedFiles = entries.filterKeys { it != SIGNATURE_FILE }
        val signer = PluginSignatureFormat.verify(signature, signedFiles).getOrElse { error ->
            return failure(error.message ?: "签名校验失败")
        }

        return PluginArchiveResult.Success(
            PluginArchiveContent(
                pluginId = manifest.pluginId,
                files = signedFiles,
                signatureBytes = signatureBytes,
                signature = signature,
                signer = signer,
            ),
        )
    }

    private fun isExecutable(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in forbiddenExtensions

    private fun readBounded(zip: ZipInputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun failure(message: String): PluginArchiveResult =
        PluginArchiveResult.Failure(message)
}
