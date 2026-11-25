package com.lifehub.app.plugin.apk

import android.content.SharedPreferences
import com.lifehub.app.data.PluginStorageFactory
import com.lifehub.pluginapi.PluginApkProtocol
import com.lifehub.pluginapi.PluginDataSnapshot
import com.lifehub.pluginapi.PluginSnapshotCollection
import com.lifehub.pluginapi.PluginSnapshotRecord
import com.lifehub.pluginapi.PluginStorage
import com.lifehub.pluginapi.PluginStorageLimits
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** 记录哪些插件已完成内置数据到 APK 的迁移，按首次安装时间识别卸载重装。 */
interface MigrationStateStore {
    fun read(pluginId: String): Long?

    fun write(pluginId: String, firstInstallTime: Long)
}

class SharedPreferencesMigrationStateStore(
    private val preferences: SharedPreferences,
) : MigrationStateStore {

    override fun read(pluginId: String): Long? =
        preferences.getLong(key(pluginId), 0L).takeIf { it > 0L }

    override fun write(pluginId: String, firstInstallTime: Long) {
        preferences.edit().putLong(key(pluginId), firstInstallTime).commit()
    }

    private fun key(pluginId: String): String = "migrated_first_install_$pluginId"
}

/**
 * 把宿主内置记账数据一次性迁移到记账 APK 插件。
 *
 * 只有迁移成功（或确认无需迁移）后才把插件标记为 ready；在此之前内置记账继续生效，
 * 避免 APK 已安装但数据为空时出现空账本。旧数据库保留不删，作为回滚保险。
 */
class LedgerApkMigration(
    private val state: MigrationStateStore,
    private val storageFactory: PluginStorageFactory,
    private val installedFirstInstallTime: () -> Long?,
    private val exportPayload: () -> String?,
    private val importPayload: (String) -> Result<Unit>,
    private val log: (String) -> Unit = {},
) {

    /**
     * 迁移内置记账数据。未安装 APK 或已经迁移时返回 [Outcome.changed] = false。
     */
    @Synchronized
    fun migrateIfNeeded(): Result<Outcome> {
        val installed = runCatching { installedFirstInstallTime() }.getOrNull()
            ?: return Result.success(Outcome(changed = false, recordCount = 0))
        if (state.read(PLUGIN_ID) == installed) {
            return Result.success(Outcome(changed = false, recordCount = 0))
        }
        return runCatching {
            val hostStorage = storageFactory.forPlugin(PLUGIN_ID, DATA_SCHEMA_VERSION)
            val collections = hostStorage.collections()
            val hostCount = collections.sumOf { hostStorage.count(it) }
            val existingPayload = exportPayload()
                ?: throw IllegalStateException("记账插件未响应导出请求")
            val existingCount = PluginApkProtocol.json
                .decodeFromString<PluginDataSnapshot>(existingPayload)
                .recordCount
            if (hostCount == 0 || existingCount > 0) {
                state.write(PLUGIN_ID, installed)
                log("ledger_apk_migration_skipped host=$hostCount apk=$existingCount")
                return@runCatching Outcome(changed = true, recordCount = existingCount)
            }
            val snapshot = buildSnapshot(hostStorage, collections)
            val payload = PluginApkProtocol.json.encodeToString(snapshot)
            val bytes = payload.toByteArray(Charsets.UTF_8).size
            if (bytes > PluginApkProtocol.MAX_PAYLOAD_BYTES) {
                throw IllegalStateException(
                    "记账数据 $bytes 字节超过迁移上限 ${PluginApkProtocol.MAX_PAYLOAD_BYTES}",
                )
            }
            importPayload(payload).getOrThrow()
            state.write(PLUGIN_ID, installed)
            log("ledger_apk_migration_done records=$hostCount bytes=$bytes")
            Outcome(changed = true, recordCount = hostCount)
        }.onFailure { error ->
            log("ledger_apk_migration_failed error=${error.message}")
        }
    }

    private fun buildSnapshot(
        storage: PluginStorage,
        collections: List<String>,
    ): PluginDataSnapshot = PluginDataSnapshot(
        pluginId = PLUGIN_ID,
        dataSchemaVersion = DATA_SCHEMA_VERSION,
        collections = collections.map { collection ->
            PluginSnapshotCollection(
                collection = collection,
                records = readAll(storage, collection),
            )
        },
    )

    private fun readAll(storage: PluginStorage, collection: String): List<PluginSnapshotRecord> =
        buildList {
            var offset = 0
            while (true) {
                val batch = storage.list(collection, PluginStorageLimits.MAX_PAGE_SIZE, offset)
                if (batch.isEmpty()) break
                addAll(batch.map { PluginSnapshotRecord(id = it.id, payload = it.payload) })
                offset += batch.size
                if (size >= PluginStorageLimits.MAX_RECORDS_PER_COLLECTION) break
            }
        }

    data class Outcome(val changed: Boolean, val recordCount: Int)

    companion object {
        const val PLUGIN_ID: String = "com.lifehub.plugin.ledger"
        private const val DATA_SCHEMA_VERSION = 1
    }
}
