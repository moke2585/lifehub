package com.lifehub.devharness

import com.lifehub.app.plugin.runtime.archive.PluginArchiveReader
import com.lifehub.app.plugin.runtime.archive.PluginArchiveResult
import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginAction
import com.lifehub.pluginapi.PluginHomeWidgetTypes
import com.lifehub.pluginapi.PluginManifest
import com.lifehub.pluginapi.PluginManifestParser
import com.lifehub.pluginapi.PluginManifestValidator
import com.lifehub.pluginapi.PluginNode
import com.lifehub.pluginapi.PluginPageParser
import com.lifehub.pluginapi.PluginPageValidator
import com.lifehub.pluginapi.PluginPathRules
import com.lifehub.pluginapi.PluginSchemaLimits
import java.io.File

enum class IssueSeverity {
    ERROR,
    WARNING,
}

data class HarnessIssue(
    val severity: IssueSeverity,
    val file: String,
    val message: String,
)

data class PluginPackageReport(
    val folder: File,
    val pluginId: String?,
    val manifest: PluginManifest?,
    val pageIds: List<String>,
    val hostActions: List<String>,
    val dataSources: List<String>,
    val issues: List<HarnessIssue>,
    val homeWidgets: List<String> = emptyList(),
    val signerFingerprint: String? = null,
) {
    val errorCount: Int get() = issues.count { it.severity == IssueSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == IssueSeverity.WARNING }
}

data class HarnessReport(
    val root: File,
    val packages: List<PluginPackageReport>,
    val issues: List<HarnessIssue>,
) {
    val errors: List<HarnessIssue>
        get() = issues + packages.flatMap { pkg -> pkg.issues.filter { it.severity == IssueSeverity.ERROR } }

    val warnings: List<HarnessIssue>
        get() = issues + packages.flatMap { pkg -> pkg.issues.filter { it.severity == IssueSeverity.WARNING } }
}

/**
 * 在不启动 Android 宿主的情况下校验本地插件包。
 *
 * 校验范围与宿主注册表保持一致：manifest、页面 schema、路径安全、文件存在性、
 * 页面大小和页面数量；同时输出需要宿主注册的 action 与数据源，便于调试。
 */
class PluginPackageValidator(
    private val hostInfo: HostInfo,
    private val maxPackageBytes: Long = DEFAULT_MAX_PACKAGE_BYTES,
) {

    fun validate(root: File): HarnessReport {
        if (!root.exists()) {
            return HarnessReport(root, emptyList(), listOf(error(root.path, "路径不存在")))
        }
        if (root.isFile) {
            return validateArchive(root)
        }
        if (!root.isDirectory) {
            return HarnessReport(root, emptyList(), listOf(error(root.path, "路径不是目录或 .lhplugin 文件")))
        }

        val packageDirs = if (File(root, MANIFEST_FILE).isFile) {
            listOf(root)
        } else {
            root.listFiles { file -> file.isDirectory }
                ?.sortedBy { it.name }
                .orEmpty()
        }

        if (packageDirs.isEmpty()) {
            return HarnessReport(
                root,
                emptyList(),
                listOf(error(root.path, "没有找到包含 $MANIFEST_FILE 的插件目录")),
            )
        }

        val seenPluginIds = mutableMapOf<String, File>()
        val packages = packageDirs.map { dir -> validatePackage(dir, seenPluginIds) }
        return HarnessReport(root, packages, emptyList())
    }

    private fun validateArchive(file: File): HarnessReport {
        val bytes = runCatching { file.readBytes() }.getOrElse { error ->
            return HarnessReport(
                file,
                emptyList(),
                listOf(error(file.path, "无法读取插件包：${error.message}")),
            )
        }
        val content = when (val result = PluginArchiveReader.read(bytes)) {
            is PluginArchiveResult.Failure -> {
                return HarnessReport(
                    file,
                    emptyList(),
                    listOf(error(file.path, result.message)),
                )
            }

            is PluginArchiveResult.Success -> result.content
        }

        val tempDir = java.nio.file.Files.createTempDirectory("lhplugin-").toFile()
        return try {
            val pluginDir = File(tempDir, content.pluginId).apply { mkdirs() }
            content.files.forEach { (path, data) ->
                val target = File(pluginDir, path)
                target.parentFile?.mkdirs()
                target.writeBytes(data)
            }
            File(pluginDir, SIGNATURE_FILE).writeBytes(content.signatureBytes)
            val report = validatePackage(pluginDir, mutableMapOf(), signatureVerified = true)
            HarnessReport(
                root = file,
                packages = listOf(
                    report.copy(
                        folder = file,
                        signerFingerprint = content.signer.fingerprint,
                    ),
                ),
                issues = emptyList(),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun validatePackage(
        dir: File,
        seenPluginIds: MutableMap<String, File>,
        signatureVerified: Boolean = false,
    ): PluginPackageReport {
        val issues = mutableListOf<HarnessIssue>()
        fun report(severity: IssueSeverity, file: String, message: String) {
            issues += HarnessIssue(severity, file, message)
        }

        val manifestFile = File(dir, MANIFEST_FILE)
        if (!manifestFile.isFile) {
            report(IssueSeverity.ERROR, MANIFEST_FILE, "缺少 $MANIFEST_FILE")
            return PluginPackageReport(dir, null, null, emptyList(), emptyList(), emptyList(), issues)
        }
        if (manifestFile.length() > MAX_MANIFEST_BYTES) {
            report(
                IssueSeverity.ERROR,
                MANIFEST_FILE,
                "manifest 大小 ${manifestFile.length()} 字节超过上限 $MAX_MANIFEST_BYTES",
            )
            return PluginPackageReport(dir, null, null, emptyList(), emptyList(), emptyList(), issues)
        }

        val raw = runCatching { manifestFile.readText(Charsets.UTF_8) }.getOrElse { error ->
            report(IssueSeverity.ERROR, MANIFEST_FILE, "无法读取 manifest：${error.message}")
            return PluginPackageReport(dir, null, null, emptyList(), emptyList(), emptyList(), issues)
        }
        val manifest = PluginManifestParser.parse(raw).getOrElse { error ->
            report(IssueSeverity.ERROR, MANIFEST_FILE, "manifest 无法解析：${error.message}")
            return PluginPackageReport(dir, null, null, emptyList(), emptyList(), emptyList(), issues)
        }

        if (manifest.pluginId != dir.name) {
            report(
                IssueSeverity.ERROR,
                MANIFEST_FILE,
                "目录名 ${dir.name} 与 pluginId ${manifest.pluginId} 不一致",
            )
        }
        val previous = seenPluginIds.putIfAbsent(manifest.pluginId, dir)
        if (previous != null) {
            report(
                IssueSeverity.ERROR,
                MANIFEST_FILE,
                "pluginId ${manifest.pluginId} 已在 ${previous.name} 中使用",
            )
        }

        PluginManifestValidator.validate(manifest, hostInfo).forEach { issue ->
            report(IssueSeverity.ERROR, MANIFEST_FILE, "[${issue.code}] ${issue.message}")
        }

        // dataSchemaVersion > 1 时，迁移是否注册由宿主 PluginDataSchemaManager 在运行时强制校验；
        // 静态校验器无法看到 Kotlin 侧的迁移表，因此这里不给出容易误报的 warning。
        if (!signatureVerified && File(dir, SIGNATURE_FILE).exists()) {
            report(
                IssueSeverity.WARNING,
                SIGNATURE_FILE,
                "目录形式不校验 manifest.sig；请对 .lhplugin 文件运行校验以验证签名",
            )
        }

        val declaredPageIds = manifest.pages.map { it.id }.toSet()
        val validPageIds = mutableListOf<String>()
        val hostActions = linkedSetOf<String>()
        val dataSources = linkedSetOf<String>()

        manifest.homeWidgets.forEach { widget ->
            if (widget.type !in PluginHomeWidgetTypes.supported) {
                report(
                    IssueSeverity.WARNING,
                    MANIFEST_FILE,
                    "未知主页小组件类型 ${widget.type}，宿主会忽略该组件",
                )
            }
            widget.sourceId?.takeIf { it.isNotBlank() }?.let { dataSources += it }
            widget.action?.let { collectAction(it, hostActions) }
        }
        manifest.search?.sourceId?.takeIf { it.isNotBlank() }?.let { dataSources += it }

        manifest.pages.forEach { page ->
            if (!PluginPathRules.isSafeRelativePath(page.file)) {
                report(IssueSeverity.ERROR, page.file, "页面文件路径不安全")
                return@forEach
            }
            val pageFile = File(dir, page.file)
            if (!pageFile.isFile) {
                report(IssueSeverity.ERROR, page.file, "页面文件不存在")
                return@forEach
            }
            if (pageFile.length() > PluginSchemaLimits.MAX_PAGE_BYTES) {
                report(
                    IssueSeverity.ERROR,
                    page.file,
                    "页面大小 ${pageFile.length()} 字节超过上限 ${PluginSchemaLimits.MAX_PAGE_BYTES}",
                )
                return@forEach
            }
            val pageRaw = runCatching { pageFile.readText(Charsets.UTF_8) }.getOrElse { error ->
                report(IssueSeverity.ERROR, page.file, "无法读取页面：${error.message}")
                return@forEach
            }
            val document = PluginPageParser.parse(pageRaw).getOrElse { error ->
                report(IssueSeverity.ERROR, page.file, "页面 JSON 无法解析：${error.message}")
                return@forEach
            }
            PluginPageValidator.validate(document, page.id, declaredPageIds).forEach { issue ->
                report(IssueSeverity.ERROR, page.file, "[${issue.code}] ${issue.message}")
            }
            if (document.pageId == page.id) {
                validPageIds += page.id
            }
            collectDependencies(document.nodes, hostActions, dataSources)
        }

        val totalBytes = dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        if (totalBytes > maxPackageBytes) {
            report(
                IssueSeverity.WARNING,
                MANIFEST_FILE,
                "插件包总大小 $totalBytes 字节超过建议上限 $maxPackageBytes",
            )
        }

        return PluginPackageReport(
            folder = dir,
            pluginId = manifest.pluginId,
            manifest = manifest,
            pageIds = validPageIds,
            hostActions = hostActions.toList(),
            dataSources = dataSources.toList(),
            homeWidgets = manifest.homeWidgets.map { "${it.id}(${it.type})" },
            issues = issues,
        )
    }

    private fun collectDependencies(
        nodes: List<PluginNode>,
        hostActions: MutableSet<String>,
        dataSources: MutableSet<String>,
    ) {
        nodes.forEach { node ->
            when (node) {
                is PluginNode.Section -> collectDependencies(node.children, hostActions, dataSources)
                is PluginNode.Button -> collectAction(node.action, hostActions)
                is PluginNode.Input -> node.action?.let { collectAction(it, hostActions) }
                is PluginNode.DateStrip -> node.action?.let { collectAction(it, hostActions) }
                is PluginNode.Chips -> node.action?.let { collectAction(it, hostActions) }
                is PluginNode.PeriodNav -> {
                    dataSources += node.sourceId
                    collectAction(node.shiftAction, hostActions)
                }

                is PluginNode.RecordList -> dataSources += node.sourceId
                is PluginNode.Metrics -> dataSources += node.sourceId
                is PluginNode.BarChart -> dataSources += node.sourceId
                is PluginNode.DonutChart -> dataSources += node.sourceId
                is PluginNode.GeoMap -> {
                    dataSources += node.sourceId
                    node.action?.let { collectAction(it, hostActions) }
                }

                else -> Unit
            }
        }
    }

    private fun collectAction(action: PluginAction, hostActions: MutableSet<String>) {
        if (action is PluginAction.Host) {
            hostActions += action.actionId
        }
    }

    private fun error(file: String, message: String): HarnessIssue =
        HarnessIssue(IssueSeverity.ERROR, file, message)

    companion object {
        // 内置地图插件包含离线的省市县边界与地区搜索索引；页面与 manifest 仍受独立的小限额约束。
        const val DEFAULT_MAX_PACKAGE_BYTES: Long = 10L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES: Long = 64L * 1024
        private const val MANIFEST_FILE = "manifest.json"
        private const val SIGNATURE_FILE = "manifest.sig"
    }
}

/**
 * 生命周期模拟：不执行宿主 Kotlin handler，只根据 manifest 声明输出预期状态流转。
 */
object LifecycleSimulator {
    fun summary(manifest: PluginManifest, denyCapabilities: Boolean): List<String> {
        val capabilityIds = manifest.capabilities.map { it.id }
        return when {
            capabilityIds.isEmpty() -> listOf(
                "INSTALLED -> ENABLED -> DISABLED -> UNINSTALLED -> INSTALLED（数据保留）",
                "启用无需能力确认",
            )

            denyCapabilities -> listOf(
                "INSTALLED -> ENABLE 被用户拒绝，保持 INSTALLED",
                "未授予能力：${capabilityIds.joinToString()}",
            )

            else -> listOf(
                "INSTALLED -> ENABLED -> DISABLED -> UNINSTALLED -> INSTALLED（数据保留）",
                "启用需用户确认 ${capabilityIds.size} 项能力：${capabilityIds.joinToString()}",
            )
        }
    }
}
