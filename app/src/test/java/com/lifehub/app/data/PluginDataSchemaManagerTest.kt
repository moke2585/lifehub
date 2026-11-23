package com.lifehub.app.data

import com.lifehub.app.FakePluginStorage
import com.lifehub.app.FakeSchemaStore
import com.lifehub.pluginapi.PluginDataMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDataSchemaManagerTest {

    private val storage = FakePluginStorage()
    private val store = FakeSchemaStore()

    @Test
    fun stampsCurrentVersionOnFirstUse() {
        val manager = PluginDataSchemaManager(store, storage, migration = null)

        manager.ensureVersion("com.example.plugin", 1)

        assertEquals(1, store.version)
        assertEquals(0, store.transactions)
    }

    @Test
    fun doesNothingWhenVersionsMatch() {
        store.version = 2
        var migrated = false
        val manager = PluginDataSchemaManager(
            store,
            storage,
            PluginDataMigration { _, _, _ -> migrated = true },
        )

        manager.ensureVersion("com.example.plugin", 2)

        assertEquals(2, store.version)
        assertTrue(!migrated)
    }

    @Test
    fun runsMigrationInsideTransactionAndUpdatesVersion() {
        store.version = 1
        var observedFrom = 0
        var observedTo = 0
        val manager = PluginDataSchemaManager(
            store,
            storage,
            PluginDataMigration { _, from, to ->
                observedFrom = from
                observedTo = to
            },
        )

        manager.ensureVersion("com.example.plugin", 2)

        assertEquals(1, observedFrom)
        assertEquals(2, observedTo)
        assertEquals(2, store.version)
        assertEquals(1, store.transactions)
    }

    @Test
    fun failedMigrationKeepsOldVersion() {
        store.version = 1
        val manager = PluginDataSchemaManager(
            store,
            storage,
            PluginDataMigration { _, _, _ -> error("boom") },
        )

        val error = runCatching { manager.ensureVersion("com.example.plugin", 2) }.exceptionOrNull()

        assertTrue(error is PluginDataMigrationException)
        assertEquals(1, store.version)
    }

    @Test
    fun rejectsMissingMigration() {
        store.version = 1
        val manager = PluginDataSchemaManager(store, storage, migration = null)

        val error = runCatching { manager.ensureVersion("com.example.plugin", 2) }.exceptionOrNull()

        assertTrue(error is PluginDataMigrationException)
        assertTrue((error as PluginDataMigrationException).message.orEmpty().contains("缺少"))
    }

    @Test
    fun rejectsDowngrade() {
        store.version = 3
        val manager = PluginDataSchemaManager(store, storage, migration = null)

        val error = runCatching { manager.ensureVersion("com.example.plugin", 2) }.exceptionOrNull()

        assertTrue(error is PluginDataMigrationException)
        assertEquals(3, store.version)
        assertNull(storage.get("meta", "any"))
    }
}
