package com.lifehub.app.plugin.zip

import com.lifehub.app.plugins.testing.PluginArchiveTestBuilder
import com.lifehub.pluginapi.HostInfo
import java.io.File
import java.security.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginZipImporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var pluginsDir: File
    private lateinit var importDir: File
    private lateinit var signerStore: MemorySignerStore
    private lateinit var importer: PluginZipImporter
    private val keyPair = PluginArchiveTestBuilder.keyPair()

    @Before
    fun setUp() {
        pluginsDir = File(temp.root, "plugins")
        importDir = File(temp.root, "import")
        signerStore = MemorySignerStore()
        importer = importer()
    }

    @Test
    fun installsSignedArchiveAndPinsSigner() {
        val preview = inspect(archive())

        assertFalse(preview.signerKnown)
        assertFalse(preview.isUpdate)

        val installed = importer.commit(preview)

        assertTrue(installed.toString(), installed is PluginImportResult.Installed)
        assertTrue(File(pluginsDir, "${PLUGIN_ID}/manifest.json").isFile)
        assertTrue(File(pluginsDir, "${PLUGIN_ID}/pages/home.json").isFile)
        assertTrue(File(pluginsDir, "${PLUGIN_ID}/manifest.sig").isFile)
        assertEquals(
            PluginArchiveTestBuilder.sha256Hex(keyPair.public.encoded),
            signerStore.fingerprint(PLUGIN_ID),
        )
    }

    @Test
    fun rejectsSignerChangeOnUpdate() {
        importer.commit(inspect(archive(version = "1.0.0")))
        val otherKey = PluginArchiveTestBuilder.keyPair()

        val result = importer.inspect(archive(version = "1.1.0", keyPair = otherKey).inputStream())

        assertTrue(result is PluginImportResult.Failure)
        assertTrue((result as PluginImportResult.Failure).message.contains("签名者"))
    }

    @Test
    fun rejectsDowngrade() {
        importer.commit(inspect(archive(version = "1.1.0")))

        val result = importer.inspect(archive(version = "1.0.0").inputStream())

        assertTrue(result is PluginImportResult.Failure)
        assertTrue((result as PluginImportResult.Failure).message.contains("降级"))
    }

    @Test
    fun rejectsBuiltInPluginId() {
        val conflict = importer(builtIn = setOf(PLUGIN_ID))

        val result = conflict.inspect(archive().inputStream())

        assertTrue(result is PluginImportResult.Failure)
        assertTrue((result as PluginImportResult.Failure).message.contains("内置插件"))
    }

    @Test
    fun rejectsIncompatibleManifest() {
        val files = mapOf(
            "manifest.json" to PluginArchiveTestBuilder.manifestJson(
                minHostVersion = 2,
            ).toByteArray(),
            "pages/home.json" to PluginArchiveTestBuilder.pageJson().toByteArray(),
        )
        val archive = PluginArchiveTestBuilder.archive(files = files, keyPair = keyPair)

        val result = importer.inspect(archive.inputStream())

        assertTrue(result is PluginImportResult.Failure)
        assertTrue((result as PluginImportResult.Failure).message.contains("不兼容"))
    }

    @Test
    fun rejectsDataSchemaChange() {
        importer.commit(inspect(archive(version = "1.0.0")))
        val files = mapOf(
            "manifest.json" to PluginArchiveTestBuilder.manifestJson(
                version = "1.1.0",
                dataSchemaVersion = 2,
            ).toByteArray(),
            "pages/home.json" to PluginArchiveTestBuilder.pageJson().toByteArray(),
        )
        val archive = PluginArchiveTestBuilder.archive(
            version = "1.1.0",
            files = files,
            keyPair = keyPair,
        )

        val result = importer.inspect(archive.inputStream())

        assertTrue(result is PluginImportResult.Failure)
        assertTrue(
            (result as PluginImportResult.Failure).message.contains("dataSchemaVersion"),
        )
    }

    @Test
    fun discardRemovesStagingDirectory() {
        val preview = inspect(archive())
        assertTrue(preview.stagingDir.exists())

        importer.discard(preview)

        assertFalse(preview.stagingDir.exists())
    }

    @Test
    fun updateReplacesInstalledFiles() {
        importer.commit(inspect(archive(version = "1.0.0")))
        val updatedPage = PluginArchiveTestBuilder.pageJson()
            .replace("内容", "更新后的内容")
        val preview = inspect(archive(version = "1.1.0", page = updatedPage))

        assertTrue(preview.isUpdate)
        assertEquals("1.0.0", preview.existingVersion)

        importer.commit(preview)

        assertTrue(
            File(pluginsDir, "${PLUGIN_ID}/pages/home.json")
                .readText()
                .contains("更新后的内容"),
        )
        assertTrue(File(pluginsDir, "${PLUGIN_ID}/manifest.json").readText().contains("1.1.0"))
    }

    @Test
    fun importsTemplateSamplePackage() {
        val template = sampleDir()
        val files = template.walkTopDown()
            .filter { it.isFile }
            .associate { file ->
                file.relativeTo(template).invariantSeparatorsPath to file.readBytes()
            }
        val archive = PluginArchiveTestBuilder.archive(
            pluginId = "com.lifehub.plugin.template",
            version = "1.0.0",
            files = files,
            keyPair = keyPair,
        )

        val preview = inspect(archive)
        val installed = importer.commit(preview)

        assertTrue(installed is PluginImportResult.Installed)
        assertTrue(
            File(pluginsDir, "com.lifehub.plugin.template/pages/home.json").isFile,
        )
    }

    private fun sampleDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "samples/com.lifehub.plugin.template")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("找不到 samples/com.lifehub.plugin.template")
    }

    private fun inspect(archive: ByteArray): PluginImportPreview {
        val result = importer.inspect(archive.inputStream())
        assertTrue(result.toString(), result is PluginImportResult.Ready)
        return (result as PluginImportResult.Ready).preview
    }

    private fun archive(
        version: String = "1.0.0",
        keyPair: KeyPair = this.keyPair,
        page: String = PluginArchiveTestBuilder.pageJson(),
    ): ByteArray {
        val files = mapOf(
            "manifest.json" to PluginArchiveTestBuilder.manifestJson(
                version = version,
            ).toByteArray(),
            "pages/home.json" to page.toByteArray(),
        )
        return PluginArchiveTestBuilder.archive(
            version = version,
            files = files,
            keyPair = keyPair,
        )
    }

    private fun importer(builtIn: Set<String> = emptySet()): PluginZipImporter =
        PluginZipImporter(
            pluginsDir = pluginsDir,
            importDir = importDir,
            hostInfo = HostInfo(versionCode = 1, versionName = "1.0.0"),
            signerStore = signerStore,
            builtInPluginIds = { builtIn },
        )

    private companion object {
        const val PLUGIN_ID = PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID
    }
}

private class MemorySignerStore : PluginSignerStore {
    private val values = mutableMapOf<String, String>()

    override fun fingerprint(pluginId: String): String? = values[pluginId]

    override fun pin(pluginId: String, fingerprint: String, keyId: String) {
        values[pluginId] = fingerprint
    }
}
