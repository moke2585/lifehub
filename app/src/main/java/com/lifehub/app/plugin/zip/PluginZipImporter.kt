package com.lifehub.app.plugin.zip

import com.lifehub.app.plugin.runtime.archive.PluginArchiveReader
import com.lifehub.app.plugin.runtime.archive.PluginArchiveResult
import com.lifehub.app.plugin.runtime.version.PluginVersion
import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginCapability
import com.lifehub.pluginapi.PluginManifest
import com.lifehub.pluginapi.PluginManifestParser
import com.lifehub.pluginapi.PluginManifestValidator
import com.lifehub.pluginapi.PluginPageParser
import com.lifehub.pluginapi.PluginPageValidator
import com.lifehub.pluginapi.PluginPathRules
import com.lifehub.pluginapi.PluginSchemaLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

sealed interface PluginImportResult {
    data class Ready(val preview: PluginImportPreview) : PluginImportResult

    data class Installed(
        val pluginId: String,
        val displayName: String,
        val version: String,
    ) : PluginImportResult

    data class Failure(val message: String) : PluginImportResult
}

/**
 * 已通过结构、签名与 schema 校验，等待用户确认的导入预览。
 * [stagingDir] 内已解压出 `<pluginId>/`，确认后原子替换到正式目录。
 */
data class PluginImportPreview(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val publisher: String,
    val capabilities: List<PluginCapability>,
    val signerKeyId: String,
    val signerFingerprint: String,
    val signerKnown: Boolean,
    val existingVersion: String?,
    val stagingDir: File,
) {
    val isUpdate: Boolean get() = existingVersion != null
}

/**
 * `.lhplugin` 离线导入：检查、暂存与原子安装。
 *
 * 导入不依赖网络，也不会自动启用插件。签名者在首次确认后按 pluginId 固定。
 */
class PluginZipImporter(
    private val pluginsDir: File,
    private val importDir: File,
    private val hostInfo: HostInfo,
    private val signerStore: PluginSignerStore,
    private val builtInPluginIds: () -> Set<String>,
    private val officialSignerFingerprints: Set<String> = emptySet(),
) {

    fun inspect(input: InputStream): PluginImportResult {
        if (!importDir.exists() && !importDir.mkdirs()) {
            return failure("无法创建导入暂存目录")
        }
        // 上一次导入被进程中断时可能留下暂存目录；每次开始新导入前清理。
        importDir.listFiles()?.forEach { it.deleteRecursively() }
        val staging = File(importDir, "stage-${UUID.randomUUID()}")
        if (!staging.mkdirs()) {
            return failure("无法创建导入暂存目录")
        }

        fun fail(message: String): PluginImportResult {
            staging.deleteRecursively()
            return failure(message)
        }

        return try {
            val bytes = readBounded(input, PluginArchiveReader.MAX_ARCHIVE_BYTES)
                ?: return fail("插件包超过 ${PluginArchiveReader.MAX_ARCHIVE_BYTES / 1024 / 1024} MB 上限")
            val content = when (val result = PluginArchiveReader.read(bytes)) {
                is PluginArchiveResult.Failure -> return fail(result.message)
                is PluginArchiveResult.Success -> result.content
            }

            val manifestText = String(content.files.getValue(MANIFEST_FILE), Charsets.UTF_8)
            val manifest = PluginManifestParser.parse(manifestText).getOrElse { error ->
                return fail("manifest 无法解析：${error.message ?: "未知错误"}")
            }

            val manifestIssues = PluginManifestValidator.validate(manifest, hostInfo)
            if (manifestIssues.isNotEmpty()) {
                return fail(
                    "插件清单不兼容：${manifestIssues.joinToString("；") { it.message }}",
                )
            }
            validatePages(manifest, content.files)?.let { message -> return fail(message) }

            if (manifest.pluginId in builtInPluginIds()) {
                return fail("插件 ID ${manifest.pluginId} 与内置插件冲突，无法导入")
            }

            val existing = existingManifest(manifest.pluginId)
            val existingVersion = existing?.version
            if (existingVersion != null &&
                PluginVersion.compare(manifest.version, existingVersion) < 0
            ) {
                return fail(
                    "不能降级：已安装 $existingVersion，新包为 ${manifest.version}",
                )
            }
            if (existing != null &&
                existing.dataSchemaVersion != manifest.dataSchemaVersion
            ) {
                return fail(
                    "外部插件 v1 不支持修改 dataSchemaVersion：" +
                        "已安装 v${existing.dataSchemaVersion}，新包 v${manifest.dataSchemaVersion}",
                )
            }

            val pinnedFingerprint = signerStore.fingerprint(manifest.pluginId)
            if (pinnedFingerprint != null && pinnedFingerprint != content.signer.fingerprint) {
                return fail("签名者与已安装版本不一致，已拒绝导入")
            }

            val pluginStage = File(staging, manifest.pluginId)
            content.files.forEach { (path, data) -> writeSafe(pluginStage, path, data) }
            writeSafe(pluginStage, SIGNATURE_FILE, content.signatureBytes)

            PluginImportResult.Ready(
                PluginImportPreview(
                    pluginId = manifest.pluginId,
                    displayName = manifest.name,
                    version = manifest.version,
                    publisher = manifest.publisher,
                    capabilities = manifest.capabilities,
                    signerKeyId = content.signer.keyId,
                    signerFingerprint = content.signer.fingerprint,
                    signerKnown = pinnedFingerprint != null ||
                        content.signer.fingerprint in officialSignerFingerprints,
                    existingVersion = existingVersion,
                    stagingDir = staging,
                ),
            )
        } catch (error: Exception) {
            fail("无法读取插件包：${error.message ?: "未知错误"}")
        }
    }

    fun commit(preview: PluginImportPreview): PluginImportResult {
        val stagedPlugin = File(preview.stagingDir, preview.pluginId)
        if (!stagedPlugin.isDirectory) {
            return failure("导入暂存内容已失效，请重新选择插件包")
        }
        if (!pluginsDir.exists() && !pluginsDir.mkdirs()) {
            return failure("无法创建插件目录")
        }

        val target = File(pluginsDir, preview.pluginId)
        val backup = File(pluginsDir, ".backup-${UUID.randomUUID()}")
        val hadExisting = target.exists()
        if (hadExisting && !target.renameTo(backup)) {
            return failure("无法替换已安装插件「${preview.displayName}」")
        }
        if (!stagedPlugin.renameTo(target)) {
            if (hadExisting) backup.renameTo(target)
            return failure("写入插件失败，已保留原版本")
        }

        return try {
            signerStore.pin(preview.pluginId, preview.signerFingerprint, preview.signerKeyId)
            backup.deleteRecursively()
            preview.stagingDir.deleteRecursively()
            PluginImportResult.Installed(
                pluginId = preview.pluginId,
                displayName = preview.displayName,
                version = preview.version,
            )
        } catch (error: Exception) {
            target.deleteRecursively()
            if (hadExisting) backup.renameTo(target)
            preview.stagingDir.deleteRecursively()
            failure("插件安装未完成：${error.message ?: "未知错误"}")
        }
    }

    fun discard(preview: PluginImportPreview) {
        preview.stagingDir.deleteRecursively()
    }

    private fun validatePages(
        manifest: PluginManifest,
        files: Map<String, ByteArray>,
    ): String? {
        val pageIds = manifest.pages.map { it.id }.toSet()
        manifest.pages.forEach { page ->
            val bytes = files[page.file]
                ?: return "缺少页面文件：${page.file}"
            if (bytes.size > PluginSchemaLimits.MAX_PAGE_BYTES) {
                return "页面 ${page.id} 超过 ${PluginSchemaLimits.MAX_PAGE_BYTES / 1024} KB 上限"
            }
            val document = PluginPageParser.parse(String(bytes, Charsets.UTF_8))
                .getOrElse { error ->
                    return "页面 ${page.id} JSON 无法解析：${error.message ?: "未知错误"}"
                }
            val issues = PluginPageValidator.validate(document, page.id, pageIds)
            if (issues.isNotEmpty()) {
                return "页面 ${page.id}：" + issues.joinToString("；") { it.message }
            }
        }
        return null
    }

    private fun existingManifest(pluginId: String): PluginManifest? {
        val manifestFile = File(File(pluginsDir, pluginId), MANIFEST_FILE)
        if (!manifestFile.isFile) return null
        return runCatching {
            PluginManifestParser.parse(manifestFile.readText(Charsets.UTF_8)).getOrNull()
        }.getOrNull()
    }

    private fun writeSafe(root: File, relativePath: String, bytes: ByteArray) {
        require(PluginPathRules.isSafeRelativePath(relativePath)) {
            "不安全的插件路径：$relativePath"
        }
        val rootCanonical = root.canonicalFile
        val target = File(root, relativePath).canonicalFile
        require(target.path.startsWith(rootCanonical.path + File.separator)) {
            "插件路径逃逸：$relativePath"
        }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun failure(message: String): PluginImportResult =
        PluginImportResult.Failure(message)

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val SIGNATURE_FILE = "manifest.sig"
    }
}
