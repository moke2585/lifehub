package com.lifehub.app.plugin

import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginManifestParser
import com.lifehub.pluginapi.PluginManifestValidator
import com.lifehub.pluginapi.PluginPageParser
import com.lifehub.pluginapi.PluginPageValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuiltInPluginAssetsTest {

    // 插件模块拆分后，资产随各自模块的 assets 目录一起打包进 APK。
    // 插件仓库与宿主仓库同级，测试从 app/ 目录向上两级定位。
    private val assetRoots = listOf(
        File("src/main/assets/plugins"),
        File("../../lifehub-plugin-ledger/src/main/assets/plugins"),
        File("../../lifehub-plugin-memo/src/main/assets/plugins"),
        File("../../lifehub-plugin-travel/src/main/assets/plugins"),
    )

    @Test
    fun allBuiltInPluginsHaveValidManifestsAndPages() {
        val pluginDirs = assetRoots
            .filter { it.isDirectory }
            .flatMap { root ->
                root.listFiles { file -> file.isDirectory }?.toList().orEmpty()
            }
            .sortedBy { it.name }
        assertTrue("未找到内置插件目录", pluginDirs.isNotEmpty())

        pluginDirs.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "manifest.json")
            assertTrue("缺少插件清单：${manifestFile.absolutePath}", manifestFile.isFile)

            val manifest = PluginManifestParser.parse(manifestFile.readText()).getOrThrow()
            assertEquals(pluginDir.name, manifest.pluginId)

            val manifestIssues = PluginManifestValidator.validate(
                manifest,
                HostInfo(versionCode = 1, versionName = "0.1.0"),
            )
            assertTrue("${pluginDir.name}: $manifestIssues", manifestIssues.isEmpty())

            val pageIds = manifest.pages.map { it.id }.toSet()
            manifest.pages.forEach { page ->
                val pageFile = File(pluginDir, page.file)
                assertTrue("缺少页面文件：${pageFile.absolutePath}", pageFile.isFile)

                val document = PluginPageParser.parse(pageFile.readText()).getOrThrow()
                val pageIssues = PluginPageValidator.validate(document, page.id, pageIds)
                assertTrue("${pluginDir.name}/${page.id}: $pageIssues", pageIssues.isEmpty())
            }
        }
    }
}
