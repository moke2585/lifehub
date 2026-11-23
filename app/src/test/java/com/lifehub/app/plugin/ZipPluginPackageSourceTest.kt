package com.lifehub.app.plugin

import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipPluginPackageSourceTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsPluginFilesFromPrivateDirectory() {
        val root = temp.newFolder("plugins")
        val pluginDir = File(root, PLUGIN_ID).apply { mkdirs() }
        File(pluginDir, "manifest.json").writeText("{\"pluginId\":\"$PLUGIN_ID\"}")

        val source = ZipPluginPackageSource(root)

        assertEquals(listOf(PLUGIN_ID), source.pluginIds())
        assertEquals(
            "{\"pluginId\":\"$PLUGIN_ID\"}",
            source.open(PLUGIN_ID, "manifest.json")!!.bufferedReader().use { it.readText() },
        )
        assertEquals(PluginPackageTrust.ZIP_SIGNED, source.trustFor(PLUGIN_ID))
    }

    @Test
    fun rejectsPathTraversal() {
        val root = temp.newFolder("plugins")
        val pluginDir = File(root, PLUGIN_ID).apply { mkdirs() }
        File(pluginDir, "manifest.json").writeText("{}")
        File(root, "secret.txt").writeText("secret")
        val source = ZipPluginPackageSource(root)

        assertNull(source.open(PLUGIN_ID, "../secret.txt"))
        assertNull(source.open(PLUGIN_ID, "pages/../../secret.txt"))
    }

    @Test
    fun missingRootHasNoPlugins() {
        val source = ZipPluginPackageSource(File(temp.root, "missing"))

        assertTrue(source.pluginIds().isEmpty())
        assertNull(source.open(PLUGIN_ID, "manifest.json"))
    }

    @Test
    fun compositePrefersFirstSourceAndKeepsPerPluginTrust() {
        val builtIn = MemorySource(mapOf(PLUGIN_ID to "built-in"), PluginPackageTrust.APK_SIGNED)
        val external = MemorySource(
            mapOf(PLUGIN_ID to "external", "com.example.external" to "zip"),
            PluginPackageTrust.ZIP_SIGNED,
        )
        val composite = CompositePluginPackageSource(listOf(builtIn, external))

        assertEquals(setOf(PLUGIN_ID, "com.example.external"), composite.pluginIds().toSet())
        assertEquals(
            "built-in",
            composite.open(PLUGIN_ID, "manifest.json")!!.bufferedReader().use { it.readText() },
        )
        assertEquals(PluginPackageTrust.APK_SIGNED, composite.trustFor(PLUGIN_ID))
        assertEquals(
            PluginPackageTrust.ZIP_SIGNED,
            composite.trustFor("com.example.external"),
        )
        assertEquals(
            PluginPackageTrust.UNTRUSTED,
            composite.trustFor("com.example.missing"),
        )
    }

    private companion object {
        const val PLUGIN_ID = "com.lifehub.plugin.hello"
    }
}

private class MemorySource(
    private val plugins: Map<String, String>,
    private val trust: PluginPackageTrust,
) : PluginPackageSource {

    override fun pluginIds(): List<String> = plugins.keys.toList()

    override fun open(pluginId: String, relativePath: String): InputStream? =
        plugins[pluginId]?.byteInputStream()

    override fun trustFor(pluginId: String): PluginPackageTrust = trust
}
