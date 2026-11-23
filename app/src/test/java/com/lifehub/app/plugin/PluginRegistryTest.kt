package com.lifehub.app.plugin

import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginIssueCode
import com.lifehub.pluginapi.PluginLifecycleEvent
import com.lifehub.pluginapi.PluginLifecycleHandler
import com.lifehub.pluginapi.PluginSchemaLimits
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class PluginRegistryTest {

    private val host = HostInfo(versionCode = 1, versionName = "0.1.0")

    @Test
    fun discoversBuiltInPluginAsInstalled() = runBlocking {
        val registry = registry(validSource())

        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.INSTALLED, plugin?.status)
        assertTrue(plugin?.issues?.isEmpty() == true)
        assertEquals("首页", plugin?.pages?.get("home")?.title)
    }

    @Test
    fun invalidPageSchemaIsIncompatible() = runBlocking {
        val files = validFiles() + ("pages/home.json" to pageJson(schemaVersion = 2))
        val registry = registry(sourceWithFiles(files))

        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.INCOMPATIBLE, plugin?.status)
        assertTrue(
            plugin?.issues?.any { it.code == PluginIssueCode.UNSUPPORTED_SCHEMA_VERSION } == true,
        )
        assertTrue(plugin?.pages?.isEmpty() == true)
    }

    @Test
    fun oversizedPageIsRejected() = runBlocking {
        val files = validFiles() +
            ("pages/home.json" to "x".repeat(PluginSchemaLimits.MAX_PAGE_BYTES + 1))
        val registry = registry(sourceWithFiles(files))

        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.INCOMPATIBLE, plugin?.status)
        assertTrue(plugin?.issues?.any { it.code == PluginIssueCode.INVALID_SCHEMA } == true)
    }

    @Test
    fun enablingPersistsAndIsIdempotent() = runBlocking {
        val store = MemoryStateStore()
        val handler = RecordingHandler()
        val registry = registry(validSource(), store, handler)
        registry.load()

        registry.setEnabled(PLUGIN_ID, true)
        registry.setEnabled(PLUGIN_ID, true)

        assertEquals(PluginStatus.ENABLED, registry.plugin(PLUGIN_ID)?.status)
        assertEquals(PluginStatus.ENABLED, store.read(PLUGIN_ID))
        assertEquals(listOf(PLUGIN_ID to PluginLifecycleEvent.ENABLE), handler.events)

        val reloaded = registry(validSource(), store, handler)
        reloaded.load()
        assertEquals(PluginStatus.ENABLED, reloaded.plugin(PLUGIN_ID)?.status)
    }

    @Test
    fun disablingIsIdempotentAndPersisted() = runBlocking {
        val store = MemoryStateStore()
        val handler = RecordingHandler()
        val registry = registry(validSource(), store, handler)
        registry.load()
        registry.setEnabled(PLUGIN_ID, true)
        handler.events.clear()

        registry.setEnabled(PLUGIN_ID, false)
        registry.setEnabled(PLUGIN_ID, false)

        assertEquals(PluginStatus.DISABLED, registry.plugin(PLUGIN_ID)?.status)
        assertEquals(PluginStatus.DISABLED, store.read(PLUGIN_ID))
        assertEquals(listOf(PLUGIN_ID to PluginLifecycleEvent.DISABLE), handler.events)
    }

    @Test
    fun incompatibleHostVersionCannotBeEnabled() = runBlocking {
        val store = MemoryStateStore()
        val registry = registry(sourceWith(manifest(minHostVersion = 2)), store)
        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.INCOMPATIBLE, plugin?.status)
        assertTrue(plugin?.issues?.any { it.code == PluginIssueCode.HOST_VERSION_TOO_LOW } == true)

        val result = registry.setEnabled(PLUGIN_ID, true)
        assertTrue(result is PluginActionResult.Failure)
        assertNull(store.read(PLUGIN_ID))
    }

    @Test
    fun untrustedPackageSourceIsRejected() = runBlocking {
        val registry = registry(
            FakeSource(
                plugins = mapOf(PLUGIN_ID to validFiles()),
                trust = PluginPackageTrust.UNTRUSTED,
            ),
        )

        registry.load()

        assertTrue(registry.state.value is PluginRegistryState.Failed)
        assertNull(registry.plugin(PLUGIN_ID))
    }

    @Test
    fun missingEntryPageIsIncompatible() = runBlocking {
        val files = validFiles().toMutableMap().apply { remove("pages/home.json") }
        val registry = registry(sourceWithFiles(files))
        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.INCOMPATIBLE, plugin?.status)
        assertTrue(plugin?.issues?.any { it.code == PluginIssueCode.MISSING_PAGE_FILE } == true)
    }

    @Test
    fun malformedManifestBecomesError() = runBlocking {
        val registry = registry(sourceWithFiles(mapOf("manifest.json" to "{")))
        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.ERROR, plugin?.status)
        assertNull(plugin?.manifest)
        assertTrue(plugin?.issues?.any { it.code == PluginIssueCode.MANIFEST_UNREADABLE } == true)
    }

    @Test
    fun unknownCapabilityIsIncompatible() = runBlocking {
        val manifest = manifest(
            capabilities = """[{"id":"network.public","reason":"访问网络"}]""",
        )
        val registry = registry(sourceWith(manifest))
        registry.load()

        val plugin = registry.plugin(PLUGIN_ID)
        assertEquals(PluginStatus.INCOMPATIBLE, plugin?.status)
        assertTrue(plugin?.issues?.any { it.code == PluginIssueCode.UNKNOWN_CAPABILITY } == true)
    }

    @Test
    fun duplicatePluginIdIsLoadedOnce() = runBlocking {
        val source = FakeSource(
            mapOf(
                "another-folder" to validFiles(),
                PLUGIN_ID to validFiles(),
            ),
        )
        val registry = registry(source)
        registry.load()

        val ready = registry.state.value as PluginRegistryState.Ready
        assertEquals(1, ready.plugins.count { it.pluginId == PLUGIN_ID })
    }

    @Test
    fun lifecycleFailureDoesNotBreakStateChange() = runBlocking {
        val handler = PluginLifecycleHandler { _, _ -> error("boom") }
        val registry = registry(validSource(), handler = handler)
        registry.load()

        val result = registry.setEnabled(PLUGIN_ID, true)

        assertTrue(result is PluginActionResult.Success)
        assertEquals(PluginStatus.ENABLED, registry.plugin(PLUGIN_ID)?.status)
    }

    @Test
    fun uninstallRetainsDataStateAndCanReinstall() = runBlocking {
        val store = MemoryStateStore()
        val handler = RecordingHandler()
        val registry = registry(validSource(), store, handler)
        registry.load()
        registry.setEnabled(PLUGIN_ID, true)
        handler.events.clear()

        val uninstalled = registry.uninstall(PLUGIN_ID)

        assertTrue(uninstalled is PluginActionResult.Success)
        assertEquals(PluginStatus.UNINSTALLED, registry.plugin(PLUGIN_ID)?.status)
        assertEquals(PluginStatus.UNINSTALLED, store.read(PLUGIN_ID))
        assertEquals(listOf(PLUGIN_ID to PluginLifecycleEvent.UNINSTALL), handler.events)
        assertEquals("首页", registry.plugin(PLUGIN_ID)?.pages?.get("home")?.title)

        handler.events.clear()
        val reinstalled = registry.reinstall(PLUGIN_ID)

        assertTrue(reinstalled is PluginActionResult.Success)
        assertEquals(PluginStatus.INSTALLED, registry.plugin(PLUGIN_ID)?.status)
        assertNull(store.read(PLUGIN_ID))
        assertEquals(listOf(PLUGIN_ID to PluginLifecycleEvent.INSTALL), handler.events)
    }

    @Test
    fun zipSignedSourceIsMarkedExternal() = runBlocking {
        val registry = registry(
            FakeSource(
                plugins = mapOf(PLUGIN_ID to validFiles()),
                trust = PluginPackageTrust.ZIP_SIGNED,
            ),
        )

        registry.load()

        assertEquals(PluginSource.EXTERNAL_ZIP, registry.plugin(PLUGIN_ID)?.source)
        assertEquals(PluginStatus.INSTALLED, registry.plugin(PLUGIN_ID)?.status)
    }

    @Test
    fun apkPluginSourceIsMarkedExternal() = runBlocking {
        val registry = registry(
            FakeSource(
                plugins = mapOf(PLUGIN_ID to validFiles()),
                trust = PluginPackageTrust.APK_PLUGIN_SIGNED,
            ),
        )

        registry.load()

        assertEquals(PluginSource.EXTERNAL_APK, registry.plugin(PLUGIN_ID)?.source)
        assertEquals(PluginStatus.INSTALLED, registry.plugin(PLUGIN_ID)?.status)
    }

    @Test
    fun missingApkPlaceholderIsListedAsNotInstalled() = runBlocking {
        val placeholder = com.lifehub.pluginapi.PluginDescriptor(
            pluginId = "com.lifehub.plugin.memo",
            displayName = "任务清单",
            manifest = null,
            status = PluginStatus.NOT_INSTALLED,
            source = PluginSource.EXTERNAL_APK,
        )
        val registry = registry(
            FakeSource(
                plugins = mapOf(PLUGIN_ID to validFiles()),
                placeholders = listOf(placeholder),
            ),
        )

        registry.load()

        val plugins = (registry.state.value as PluginRegistryState.Ready).plugins
        assertEquals(2, plugins.size)
        assertEquals(PluginStatus.NOT_INSTALLED, registry.plugin("com.lifehub.plugin.memo")?.status)
        assertEquals("任务清单", registry.plugin("com.lifehub.plugin.memo")?.displayName)
    }

    @Test
    fun reloadDiscoversNewPlugins() = runBlocking {
        val source = MutableFakeSource(mutableMapOf(PLUGIN_ID to validFiles()))
        val registry = registry(source)
        registry.load()
        assertEquals(1, (registry.state.value as PluginRegistryState.Ready).plugins.size)

        source.plugins["com.example.another"] = mapOf(
            "manifest.json" to manifest(pluginId = "com.example.another"),
            "pages/home.json" to pageJson(),
        )
        registry.reload()

        assertEquals(2, (registry.state.value as PluginRegistryState.Ready).plugins.size)
    }

    private fun registry(
        source: PluginPackageSource,
        store: MemoryStateStore = MemoryStateStore(),
        handler: PluginLifecycleHandler = RecordingHandler(),
    ): PluginRegistry = PluginRegistry(
        packages = source,
        stateStore = store,
        hostInfo = host,
        lifecycleHandler = handler,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun validSource(): PluginPackageSource = sourceWithFiles(validFiles())

    private fun sourceWith(manifestJson: String): PluginPackageSource =
        sourceWithFiles(validFiles() + ("manifest.json" to manifestJson))

    private fun sourceWithFiles(files: Map<String, String>): PluginPackageSource =
        FakeSource(mapOf(PLUGIN_ID to files))

    private fun validFiles(): Map<String, String> = mapOf(
        "manifest.json" to manifest(),
        "pages/home.json" to pageJson(),
    )

    private fun pageJson(
        pageId: String = "home",
        schemaVersion: Int = 1,
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "pageId": "$pageId",
          "title": "首页",
          "nodes": [{"type":"text","text":"内容"}]
        }
    """.trimIndent()

    private fun manifest(
        pluginId: String = PLUGIN_ID,
        minHostVersion: Int = 1,
        pluginSdkVersion: Int = 1,
        entryPage: String = "home",
        capabilities: String = """[{"id":"storage.private","reason":"保存数据"}]""",
    ): String = """
        {
          "pluginId": "$pluginId",
          "name": "示例插件",
          "version": "1.0.0",
          "description": "测试插件",
          "publisher": "LifeHub",
          "minHostVersion": $minHostVersion,
          "pluginSdkVersion": $pluginSdkVersion,
          "capabilities": $capabilities,
          "entryPage": "$entryPage",
          "pages": [{"id":"home","title":"首页","file":"pages/home.json"}],
          "dataSchemaVersion": 1
        }
    """.trimIndent()

    private companion object {
        const val PLUGIN_ID = "com.lifehub.plugin.hello"
    }
}

private class FakeSource(
    private val plugins: Map<String, Map<String, String>>,
    private val trust: PluginPackageTrust = PluginPackageTrust.APK_SIGNED,
    private val placeholders: List<com.lifehub.pluginapi.PluginDescriptor> = emptyList(),
) : PluginPackageSource {

    override fun pluginIds(): List<String> = plugins.keys.toList()

    override fun open(pluginId: String, relativePath: String): InputStream? =
        plugins[pluginId]?.get(relativePath)?.byteInputStream()

    override fun trustFor(pluginId: String): PluginPackageTrust = trust

    override fun placeholders(): List<com.lifehub.pluginapi.PluginDescriptor> = placeholders
}

private class MutableFakeSource(
    val plugins: MutableMap<String, Map<String, String>>,
) : PluginPackageSource {

    override fun pluginIds(): List<String> = plugins.keys.toList()

    override fun open(pluginId: String, relativePath: String): InputStream? =
        plugins[pluginId]?.get(relativePath)?.byteInputStream()

    override fun trustFor(pluginId: String): PluginPackageTrust =
        PluginPackageTrust.APK_SIGNED
}

private class MemoryStateStore : PluginStateStore {
    val values = mutableMapOf<String, PluginStatus>()

    override fun read(pluginId: String): PluginStatus? = values[pluginId]

    override fun write(pluginId: String, status: PluginStatus) {
        values[pluginId] = status
    }

    override fun clear(pluginId: String) {
        values.remove(pluginId)
    }
}

private class RecordingHandler : PluginLifecycleHandler {
    val events = mutableListOf<Pair<String, PluginLifecycleEvent>>()

    override fun onEvent(pluginId: String, event: PluginLifecycleEvent) {
        events += pluginId to event
    }
}
