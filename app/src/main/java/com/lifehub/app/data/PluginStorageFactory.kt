package com.lifehub.app.data

import android.content.Context
import com.lifehub.pluginapi.PluginDataMigration
import com.lifehub.pluginapi.PluginStorage
import java.util.concurrent.ConcurrentHashMap

fun interface PluginStorageFactory {
    fun forPlugin(pluginId: String, dataSchemaVersion: Int): PluginStorage
}

class SqlitePluginStorageFactory(
    context: Context,
    private val migrations: Map<String, PluginDataMigration> = emptyMap(),
    private val onMigrationFailure: (String, Throwable) -> Unit = { _, _ -> },
) : PluginStorageFactory {

    private val appContext = context.applicationContext
    private val storages = ConcurrentHashMap<String, PluginStorage>()

    override fun forPlugin(pluginId: String, dataSchemaVersion: Int): PluginStorage {
        require(pluginId.isNotBlank()) { "pluginId 不能为空" }
        require(dataSchemaVersion > 0) { "dataSchemaVersion 必须大于 0" }
        return storages.getOrPut(pluginId) {
            try {
                SqlitePluginStorage(appContext, pluginId, dataSchemaVersion, migrations)
            } catch (error: Exception) {
                onMigrationFailure(pluginId, error)
                throw error
            }
        }
    }
}
