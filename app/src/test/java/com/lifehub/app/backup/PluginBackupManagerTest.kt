package com.lifehub.app.backup

import com.lifehub.app.FakePluginStorage
import com.lifehub.app.testDescriptor
import com.lifehub.pluginapi.PluginApkProtocol
import com.lifehub.pluginapi.PluginDataSnapshot
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginSnapshotCollection
import com.lifehub.pluginapi.PluginSnapshotRecord
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStorage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginBackupManagerTest {

    private val pluginA = testDescriptor(pluginId = "com.lifehub.plugin.hello")
    private val pluginB = testDescriptor(pluginId = "com.lifehub.plugin.memo")

    private val storageA = FakePluginStorage().apply {
        insert("meta", "1", "greeting_count")
        insert("notes", """{"text":"hello"}""", "n1")
    }
    private val storageB = FakePluginStorage().apply {
        insert("memos", """{"text":"memo"}""", "m1")
    }

    private val password = "password123".toCharArray()

    @Test
    fun exportPreviewAndApplyRestoresData() {
        val manager = manager()
        val backup = manager.export(password)

        val preview = manager.preview(password, backup)
        assertEquals(2, preview.archive.plugins.size)
        assertEquals(2, preview.pluginData.getValue(pluginA.pluginId).collections.size)
        assertEquals(2, preview.existingRecordCounts.getValue(pluginA.pluginId))

        storageA.clear()
        storageB.clear()
        manager.apply(preview)

        assertEquals("1", storageA.get("meta", "greeting_count")?.payload)
        assertEquals(1, storageA.count("notes"))
        assertEquals(1, storageB.count("memos"))
        assertEquals(1, storageA.schemaVersion)
        assertEquals(1, storageB.schemaVersion)
    }

    @Test
    fun applyOverwritesExistingRecords() {
        val manager = manager()
        val preview = manager.preview(password, manager.export(password))
        storageA.insert("notes", """{"text":"temporary"}""", "temp")

        manager.apply(preview)

        assertEquals(0, storageA.count("temp"))
        assertEquals(1, storageA.count("notes"))
        assertEquals("""{"text":"hello"}""", storageA.get("notes", "n1")?.payload)
    }

    @Test
    fun wrongPasswordIsRejected() {
        val backup = manager().export(password)

        val error = runCatching {
            manager().preview("wrong-password".toCharArray(), backup)
        }.exceptionOrNull()

        assertTrue(error is BackupException)
    }

    @Test
    fun unknownPluginIsRejected() {
        val backup = manager().export(password)
        val onlyA = PluginBackupManager(
            pluginProvider = { listOf(pluginA) },
            storageProvider = { storageA },
            appVersion = "0.1.0",
            hostVersionCode = 1,
        )

        val error = runCatching { onlyA.preview(password, backup) }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertTrue((error as BackupException).message.orEmpty().contains("未安装"))
    }

    @Test
    fun incompatibleDataSchemaIsRejected() {
        val backup = manager().export(password)
        val upgraded = pluginA.copy(
            manifest = pluginA.manifest!!.copy(dataSchemaVersion = 2),
        )
        val upgradedManager = PluginBackupManager(
            pluginProvider = { listOf(upgraded, pluginB) },
            storageProvider = { descriptor ->
                if (descriptor.pluginId == pluginA.pluginId) storageA else storageB
            },
            appVersion = "0.1.0",
            hostVersionCode = 1,
        )

        val error = runCatching { upgradedManager.preview(password, backup) }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertTrue((error as BackupException).message.orEmpty().contains("数据版本不兼容"))
    }

    @Test
    fun exportFailsWithoutPlugins() {
        val manager = PluginBackupManager(
            pluginProvider = { emptyList() },
            storageProvider = { error("unexpected") },
            appVersion = "0.1.0",
            hostVersionCode = 1,
        )

        val error = runCatching { manager.export(password) }.exceptionOrNull()

        assertTrue(error is BackupException)
    }

    @Test
    fun externalApkBackupUsesProviderBridge() {
        val snapshot = PluginDataSnapshot(
            pluginId = pluginB.pluginId,
            dataSchemaVersion = 1,
            collections = listOf(
                PluginSnapshotCollection(
                    collection = "memos",
                    records = listOf(PluginSnapshotRecord("m1", """{"text":"memo"}""")),
                ),
            ),
        )
        val raw = PluginApkProtocol.json.encodeToString(snapshot)
        val bridge = RecordingExternalBackup(raw)
        val apkDescriptor = pluginB.copy(source = PluginSource.EXTERNAL_APK)
        val manager = PluginBackupManager(
            pluginProvider = { listOf(apkDescriptor) },
            storageProvider = { error("APK 插件不应打开宿主存储") },
            appVersion = "0.1.0",
            hostVersionCode = 1,
            externalBackup = bridge,
        )

        val backup = manager.export(password)
        val preview = manager.preview(password, backup)

        assertEquals(1, preview.archive.plugins.single().recordCount)
        assertEquals(1, preview.existingRecordCounts.getValue(pluginB.pluginId))

        manager.apply(preview)

        assertEquals(raw, bridge.imported)
    }

    @Test
    fun hostBackupMigratesIntoApkProviderBridge() {
        val backup = manager().export(password)
        val bridge = RecordingExternalBackup(payload = null)
        val apkDescriptor = pluginB.copy(source = PluginSource.EXTERNAL_APK)
        val manager = PluginBackupManager(
            pluginProvider = { listOf(pluginA, apkDescriptor) },
            storageProvider = { descriptor ->
                if (descriptor.pluginId == pluginA.pluginId) storageA
                else error("APK 插件不应打开宿主存储")
            },
            appVersion = "0.1.0",
            hostVersionCode = 1,
            externalBackup = bridge,
        )

        val preview = manager.preview(password, backup)
        manager.apply(preview)

        val imported = PluginApkProtocol.json.decodeFromString<PluginDataSnapshot>(
            requireNotNull(bridge.imported),
        )
        assertEquals(pluginB.pluginId, imported.pluginId)
        assertEquals(listOf("memos"), imported.collections.map { it.collection })
        assertEquals(listOf("m1"), imported.collections.single().records.map { it.id })
    }

    @Test
    fun suggestedFileNameUsesZipSuffixForPickerCompatibility() {
        val name = manager().suggestedFileName(now = 0L)

        assertTrue(name.startsWith("lifehub-backup-"))
        assertTrue(name.endsWith(".lifehub.zip"))
    }

    @Test
    fun v1GoldenFixtureStillImports() {
        val fixture = javaClass.classLoader
            ?.getResourceAsStream("backup-v1-fixture.lifehub.zip")
            ?: error("缺少备份 v1 fixture")
        val bytes = fixture.use { it.readBytes() }

        val manager = manager()
        val preview = manager.preview(FIXTURE_PASSWORD.toCharArray(), bytes)

        assertEquals(1, preview.archive.formatVersion)
        assertEquals(2, preview.archive.plugins.size)
        assertEquals(2, preview.pluginData.getValue(pluginA.pluginId).collections.size)
        assertEquals(1, preview.pluginData.getValue(pluginB.pluginId).collections.size)

        storageA.clear()
        storageB.clear()
        manager.apply(preview)

        assertEquals("1", storageA.get("meta", "greeting_count")?.payload)
        assertEquals(1, storageA.count("notes"))
        assertEquals(1, storageB.count("memos"))
    }

    private companion object {
        const val FIXTURE_PASSWORD = "lifehub-v1-fixture"
    }

    private fun manager(
        descriptors: List<PluginDescriptor> = listOf(pluginA, pluginB),
        storages: Map<String, PluginStorage> = mapOf(
            pluginA.pluginId to storageA,
            pluginB.pluginId to storageB,
        ),
    ): PluginBackupManager = PluginBackupManager(
        pluginProvider = { descriptors },
        storageProvider = { descriptor -> storages.getValue(descriptor.pluginId) },
        appVersion = "0.1.0",
        hostVersionCode = 1,
    )
}

private class RecordingExternalBackup(
    private val payload: String?,
) : ExternalPluginBackup {

    var imported: String? = null

    override fun export(pluginId: String): String? = payload

    override fun import(pluginId: String, payload: String) {
        imported = payload
    }

    override fun recordCount(pluginId: String): Int? =
        payload?.let {
            PluginApkProtocol.json.decodeFromString<PluginDataSnapshot>(it).recordCount
        }
}
