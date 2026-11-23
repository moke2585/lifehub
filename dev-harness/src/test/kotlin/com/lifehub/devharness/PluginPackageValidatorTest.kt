package com.lifehub.devharness

import com.lifehub.app.plugins.testing.PluginArchiveTestBuilder
import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginManifestParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PluginPackageValidatorTest {

    private val tempDirs = mutableListOf<File>()
    private val validator = PluginPackageValidator(
        hostInfo = HostInfo(versionCode = 1, versionName = "dev-harness"),
    )

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun validPackageHasNoErrors() {
        val root = tempRoot()
        writePackage(root)

        val report = validator.validate(root)

        assertEquals(1, report.packages.size)
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        assertEquals(listOf("home"), report.packages.single().pageIds)
    }

    @Test
    fun missingPageFileIsReported() {
        val root = tempRoot()
        writePackage(root, page = null)

        val report = validator.validate(root)

        assertTrue(report.errors.any { it.message.contains("页面文件不存在") })
    }

    @Test
    fun folderNameMismatchIsReported() {
        val root = tempRoot()
        writePackage(root, folder = "wrong-folder")

        val report = validator.validate(root)

        assertTrue(report.errors.any { it.message.contains("目录名") })
    }

    @Test
    fun duplicatePluginIdIsReported() {
        val root = tempRoot()
        writePackage(root, folder = "first")
        writePackage(root, folder = "second")

        val report = validator.validate(root)

        assertEquals(2, report.packages.size)
        assertTrue(report.errors.any { it.message.contains("已在") })
    }

    @Test
    fun unsafePagePathIsReported() {
        val root = tempRoot()
        writePackage(
            root,
            manifest = manifestJson(
                pages = """[{"id":"home","title":"首页","file":"../secret.json"}]""",
            ),
        )

        val report = validator.validate(root)

        assertTrue(report.errors.any { it.message.contains("路径不安全") })
    }

    @Test
    fun unsupportedSchemaVersionIsReported() {
        val root = tempRoot()
        writePackage(root, page = pageJson(schemaVersion = 2))

        val report = validator.validate(root)

        assertTrue(report.errors.any { it.message.contains("UNSUPPORTED_SCHEMA_VERSION") })
    }

    @Test
    fun collectsHostActionsAndDataSources() {
        val root = tempRoot()
        writePackage(
            root,
            page = """
                {
                  "schemaVersion": 1,
                  "pageId": "home",
                  "title": "首页",
                  "nodes": [
                    {"type":"recordList","sourceId":"records"},
                    {"type":"button","label":"提交","action":{"type":"host","actionId":"com.example.submit"}}
                  ]
                }
            """.trimIndent(),
        )

        val report = validator.validate(root)

        val pkg = report.packages.single()
        assertEquals(listOf("com.example.submit"), pkg.hostActions)
        assertEquals(listOf("records"), pkg.dataSources)
    }

    @Test
    fun lifecycleSimulatorReflectsConsentAndDenial() {
        val manifest = PluginManifestParser.parse(
            manifestJson(
                capabilities = """[{"id":"storage.private","reason":"保存数据"}]""",
            ),
        ).getOrThrow()

        val granted = LifecycleSimulator.summary(manifest, denyCapabilities = false)
        val denied = LifecycleSimulator.summary(manifest, denyCapabilities = true)

        assertTrue(granted.any { it.contains("用户确认 1 项能力") })
        assertTrue(denied.any { it.contains("拒绝") })
    }

    @Test
    fun signedArchiveIsValidated() {
        val file = File(tempRoot(), "plugin.lhplugin")
        file.writeBytes(PluginArchiveTestBuilder.archive())

        val report = validator.validate(file)

        assertEquals(1, report.packages.size)
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        assertEquals(64, report.packages.single().signerFingerprint?.length)
        assertTrue(
            report.packages.single().issues.none { it.message.contains("目录形式") },
        )
    }

    @Test
    fun archiveWithMismatchedSignatureIsReported() {
        val files = PluginArchiveTestBuilder.validFiles()
        val signature = PluginArchiveTestBuilder.signature(
            pluginId = PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            version = "2.0.0",
            files = files,
            keyPair = PluginArchiveTestBuilder.keyPair(),
        )
        val file = File(tempRoot(), "bad.lhplugin")
        file.writeBytes(
            PluginArchiveTestBuilder.zip(
                PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
                files + ("manifest.sig" to signature.toByteArray()),
            ),
        )

        val report = validator.validate(file)

        assertTrue(report.packages.isEmpty())
        assertTrue(report.errors.any { it.message.contains("version") })
    }

    private fun tempRoot(): File =
        Files.createTempDirectory("dev-harness-test").toFile().also { tempDirs += it }

    private fun writePackage(
        root: File,
        folder: String = PLUGIN_ID,
        manifest: String = manifestJson(),
        page: String? = pageJson(),
    ) {
        val dir = File(root, folder)
        assertTrue(dir.mkdirs())
        File(dir, "manifest.json").writeText(manifest)
        if (page != null) {
            val pages = File(dir, "pages")
            assertTrue(pages.mkdirs())
            File(pages, "home.json").writeText(page)
        }
    }

    private fun manifestJson(
        capabilities: String = "[]",
        pages: String = """[{"id":"home","title":"首页","file":"pages/home.json"}]""",
    ): String = """
        {
          "pluginId": "$PLUGIN_ID",
          "name": "模板插件",
          "version": "1.0.0",
          "description": "测试插件",
          "publisher": "LifeHub",
          "icon": "plugin",
          "minHostVersion": 1,
          "pluginSdkVersion": 1,
          "capabilities": $capabilities,
          "entryPage": "home",
          "pages": $pages,
          "dataSchemaVersion": 1
        }
    """.trimIndent()

    private fun pageJson(schemaVersion: Int = 1): String = """
        {
          "schemaVersion": $schemaVersion,
          "pageId": "home",
          "title": "首页",
          "nodes": [{"type":"text","text":"内容"}]
        }
    """.trimIndent()

    private companion object {
        const val PLUGIN_ID = "com.example.template"
    }
}
