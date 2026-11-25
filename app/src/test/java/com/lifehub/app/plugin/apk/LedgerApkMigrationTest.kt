package com.lifehub.app.plugin.apk

import com.lifehub.app.FakePluginStorage
import com.lifehub.pluginapi.PluginApkProtocol
import com.lifehub.pluginapi.PluginDataSnapshot
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerApkMigrationTest {

    private val pluginId = LedgerApkMigration.PLUGIN_ID

    @Test
    fun `未安装 APK 时不迁移也不排除内置`() {
        val state = FakeState()
        var imported: String? = null
        val migration = migration(
            state = state,
            host = FakePluginStorage(),
            installed = { null },
            exportPayload = { "{\"pluginId\":\"$pluginId\",\"dataSchemaVersion\":1}" },
            importPayload = { payload ->
                imported = payload
                Result.success(Unit)
            },
        )

        val outcome = migration.migrateIfNeeded().getOrThrow()

        assertFalse(outcome.changed)
        assertNull(imported)
        assertTrue(migration.readyPluginIds().isEmpty())
    }

    @Test
    fun `宿主有数据时导入快照并标记就绪`() {
        val state = FakeState()
        val host = FakePluginStorage().apply {
            insert("entries", "{\"amountMinor\":100}", "entry-1")
            insert("budget", "{\"amountMinor\":5000}", "monthly")
        }
        var imported: String? = null
        val migration = migration(
            state = state,
            host = host,
            installed = { 100L },
            exportPayload = { emptySnapshot() },
            importPayload = { payload ->
                imported = payload
                Result.success(Unit)
            },
        )

        val outcome = migration.migrateIfNeeded().getOrThrow()

        assertTrue(outcome.changed)
        assertEquals(2, outcome.recordCount)
        val snapshot = PluginApkProtocol.json.decodeFromString<PluginDataSnapshot>(
            requireNotNull(imported),
        )
        assertEquals(pluginId, snapshot.pluginId)
        assertEquals(2, snapshot.recordCount)
        assertEquals(setOf(pluginId), migration.readyPluginIds())
        assertEquals(100L, state.read(pluginId))
    }

    @Test
    fun `宿主为空时直接标记就绪`() {
        val state = FakeState()
        var imported: String? = null
        val migration = migration(
            state = state,
            host = FakePluginStorage(),
            installed = { 200L },
            exportPayload = { emptySnapshot() },
            importPayload = { payload ->
                imported = payload
                Result.success(Unit)
            },
        )

        val outcome = migration.migrateIfNeeded().getOrThrow()

        assertTrue(outcome.changed)
        assertEquals(0, outcome.recordCount)
        assertNull(imported)
        assertEquals(setOf(pluginId), migration.readyPluginIds())
    }

    @Test
    fun `APK 已有数据时不覆盖`() {
        val state = FakeState()
        var imported: String? = null
        val migration = migration(
            state = state,
            host = FakePluginStorage().apply {
                insert("entries", "{\"amountMinor\":100}", "entry-1")
            },
            installed = { 300L },
            exportPayload = {
                PluginApkProtocol.json.encodeToString(
                    PluginDataSnapshot(
                        pluginId = pluginId,
                        dataSchemaVersion = 1,
                        collections = listOf(
                            com.lifehub.pluginapi.PluginSnapshotCollection(
                                collection = "entries",
                                records = listOf(
                                    com.lifehub.pluginapi.PluginSnapshotRecord(
                                        id = "apk-entry",
                                        payload = "{}",
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            },
            importPayload = { payload ->
                imported = payload
                Result.success(Unit)
            },
        )

        val outcome = migration.migrateIfNeeded().getOrThrow()

        assertTrue(outcome.changed)
        assertNull(imported)
        assertEquals(setOf(pluginId), migration.readyPluginIds())
    }

    @Test
    fun `超过 Binder 上限时失败且不标记`() {
        val state = FakeState()
        val host = FakePluginStorage()
        repeat(9) { index ->
            host.insert("entries", "x".repeat(64 * 1024), "entry-$index")
        }
        val migration = migration(
            state = state,
            host = host,
            installed = { 400L },
            exportPayload = { emptySnapshot() },
            importPayload = { Result.success(Unit) },
        )

        val result = migration.migrateIfNeeded()

        assertTrue(result.isFailure)
        assertNull(state.read(pluginId))
        assertTrue(migration.readyPluginIds().isEmpty())
    }

    @Test
    fun `首次安装时间变化后重新迁移`() {
        val state = FakeState()
        var installed = 500L
        val host = FakePluginStorage().apply {
            insert("entries", "{\"amountMinor\":100}", "entry-1")
        }
        var importCount = 0
        val migration = migration(
            state = state,
            host = host,
            installed = { installed },
            exportPayload = { emptySnapshot() },
            importPayload = {
                importCount += 1
                Result.success(Unit)
            },
        )

        migration.migrateIfNeeded().getOrThrow()
        assertEquals(1, importCount)

        // 同一安装再次检查不重复迁移。
        val repeat = migration.migrateIfNeeded().getOrThrow()
        assertFalse(repeat.changed)
        assertEquals(1, importCount)

        // 卸载重装后首次安装时间变化，需要重新迁移。
        installed = 600L
        migration.migrateIfNeeded().getOrThrow()
        assertEquals(2, importCount)
        assertEquals(600L, state.read(pluginId))
    }

    @Test
    fun `已迁移后不再导出 APK 数据`() {
        val state = FakeState().apply { write(pluginId, 700L) }
        var exported = false
        val migration = migration(
            state = state,
            host = FakePluginStorage(),
            installed = { 700L },
            exportPayload = {
                exported = true
                emptySnapshot()
            },
            importPayload = { Result.success(Unit) },
        )

        val outcome = migration.migrateIfNeeded().getOrThrow()

        assertFalse(outcome.changed)
        assertFalse(exported)
    }

    private fun migration(
        state: MigrationStateStore,
        host: FakePluginStorage,
        installed: () -> Long?,
        exportPayload: () -> String?,
        importPayload: (String) -> Result<Unit>,
    ): LedgerApkMigration = LedgerApkMigration(
        state = state,
        storageFactory = { _, _ -> host },
        installedFirstInstallTime = installed,
        exportPayload = exportPayload,
        importPayload = importPayload,
    )

    private fun emptySnapshot(): String = PluginApkProtocol.json.encodeToString(
        PluginDataSnapshot(
            pluginId = pluginId,
            dataSchemaVersion = 1,
        ),
    )

    private class FakeState : MigrationStateStore {
        private val values = mutableMapOf<String, Long>()

        override fun read(pluginId: String): Long? = values[pluginId]

        override fun write(pluginId: String, firstInstallTime: Long) {
            values[pluginId] = firstInstallTime
        }
    }
}
