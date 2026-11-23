package com.lifehub.app.plugin

import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginPathRules
import java.io.File
import java.io.InputStream

/**
 * 读取导入到 App 私有目录的外部 `.lhplugin` 插件。
 *
 * 目录布局：`<root>/<pluginId>/manifest.json` 等。导入阶段已完成签名校验，
 * 这里只负责受限读取，并拒绝任何逃逸出插件根目录的路径。
 */
class ZipPluginPackageSource(
    private val root: File,
) : PluginPackageSource {

    override fun pluginIds(): List<String> =
        root.listFiles { file -> file.isDirectory && File(file, MANIFEST_FILE).isFile }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    override fun open(pluginId: String, relativePath: String): InputStream? {
        if (!PluginPathRules.isSafeRelativePath(pluginId) ||
            !PluginPathRules.isSafeRelativePath(relativePath)
        ) {
            return null
        }
        return runCatching {
            val pluginRoot = File(root, pluginId).canonicalFile
            val target = File(pluginRoot, relativePath).canonicalFile
            if (!target.path.startsWith(pluginRoot.path + File.separator)) {
                return null
            }
            target.takeIf { it.isFile }?.inputStream()
        }.getOrNull()
    }

    override fun trustFor(pluginId: String): PluginPackageTrust =
        PluginPackageTrust.ZIP_SIGNED

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}

/**
 * 按插件查找所属包源。排在前面的来源优先，内置插件不会被外部同 ID 包覆盖。
 */
class CompositePluginPackageSource(
    private val sources: List<PluginPackageSource>,
) : PluginPackageSource {

    override fun pluginIds(): List<String> =
        sources.flatMap { it.pluginIds() }.distinct()

    override fun open(pluginId: String, relativePath: String): InputStream? =
        owner(pluginId)?.open(pluginId, relativePath)

    override fun trustFor(pluginId: String): PluginPackageTrust =
        owner(pluginId)?.trustFor(pluginId) ?: PluginPackageTrust.UNTRUSTED

    override fun placeholders(): List<PluginDescriptor> =
        sources.flatMap { it.placeholders() }
            .filter { placeholder -> placeholder.pluginId !in pluginIds() }
            .distinctBy { it.pluginId }

    private fun owner(pluginId: String): PluginPackageSource? =
        sources.firstOrNull { pluginId in it.pluginIds() }
}
