package com.lifehub.app.market.install

import android.content.Context
import android.os.Build
import com.lifehub.app.market.download.ArtifactDownloader
import com.lifehub.app.plugin.apk.ApkPluginPackageSource
import com.lifehub.app.plugin.runtime.archive.PluginArchiveReader
import com.lifehub.app.plugin.runtime.index.IndexedPlugin
import com.lifehub.app.plugin.runtime.version.PluginVersion
import com.lifehub.app.plugin.zip.PluginImportPreview
import com.lifehub.app.plugin.zip.PluginImportResult
import com.lifehub.app.plugin.zip.PluginZipImporter

/**
 * 市场安装器：ZIP 复用 .lhplugin 导入流程，APK 走校验 + PackageInstaller。
 */
class MarketplaceInstaller(
    private val context: Context,
    private val downloader: ArtifactDownloader,
    private val zipImporter: PluginZipImporter,
    private val apkPluginSource: ApkPluginPackageSource,
) {

    fun inspectZip(entry: IndexedPlugin): Result<PluginImportPreview> = runCatching {
        require(entry.isZip) { "索引条目不是 .lhplugin" }
        val file = downloader.download(
            url = entry.url,
            expectedSha256 = entry.sha256,
            maxBytes = MAX_ZIP_BYTES,
            fileName = "${entry.pluginId}-${entry.version}.lhplugin",
        )
        val result = file.inputStream().use { input -> zipImporter.inspect(input) }
        val preview = when (result) {
            is PluginImportResult.Ready -> result.preview
            is PluginImportResult.Failure -> error(result.message)
            is PluginImportResult.Installed -> error("插件包状态异常")
        }
        require(preview.pluginId == entry.pluginId) { "插件 ID 与索引不一致" }
        require(PluginVersion.compare(preview.version, entry.version) == 0) {
            "插件版本与索引不一致：${preview.version}"
        }
        preview
    }

    fun commitZip(preview: PluginImportPreview): Result<PluginImportResult.Installed> = runCatching {
        when (val result = zipImporter.commit(preview)) {
            is PluginImportResult.Installed -> result
            is PluginImportResult.Failure -> error(result.message)
            is PluginImportResult.Ready -> error("插件包状态异常")
        }
    }

    fun discardZip(preview: PluginImportPreview) {
        zipImporter.discard(preview)
    }

    fun prepareApk(entry: IndexedPlugin): Result<VerifiedApk> = runCatching {
        require(entry.isApk) { "索引条目不是 APK" }
        val applicationId = entry.applicationId ?: error("索引缺少 applicationId")
        val file = downloader.download(
            url = entry.url,
            expectedSha256 = entry.sha256,
            maxBytes = MAX_APK_BYTES,
            fileName = "$applicationId-${entry.version}.apk",
        )
        val handle = apkPluginSource.installedHandle(entry.pluginId)
        ApkArtifactVerifier(context.packageManager)
            .verify(
                file = file,
                entry = entry,
                installedVersionCode = handle?.versionCode,
                installedSigner = handle?.signerFingerprint,
            )
            .getOrThrow()
    }

    fun installApk(verified: VerifiedApk): Result<Unit> =
        ApkInstallSession(context).install(verified)

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    private companion object {
        val MAX_ZIP_BYTES: Long = PluginArchiveReader.MAX_ARCHIVE_BYTES.toLong()
        const val MAX_APK_BYTES: Long = 64L * 1024 * 1024
    }
}
