package com.lifehub.app.data

import com.lifehub.pluginapi.PluginDataMigration
import com.lifehub.pluginapi.PluginStorage

/**
 * 插件数据库的版本元数据与事务能力。只暴露给宿主数据层，不进入插件 SDK。
 */
interface PluginDataSchemaStore {
    fun readDataSchemaVersion(): Int?

    fun writeDataSchemaVersion(version: Int)

    fun <T> transaction(block: () -> T): T
}

class PluginDataMigrationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * 在插件首次使用或版本变化时校准数据 schema 版本。
 * 迁移在事务中执行；异常会回滚数据与版本号，旧数据保持不变。
 */
class PluginDataSchemaManager(
    private val store: PluginDataSchemaStore,
    private val storage: PluginStorage,
    private val migration: PluginDataMigration?,
) {
    fun ensureVersion(pluginId: String, targetVersion: Int) {
        require(targetVersion > 0) { "目标数据版本必须大于 0" }

        val currentVersion = store.readDataSchemaVersion()
        if (currentVersion == null) {
            store.writeDataSchemaVersion(targetVersion)
            return
        }
        if (currentVersion == targetVersion) return
        if (currentVersion > targetVersion) {
            throw PluginDataMigrationException(
                "插件 $pluginId 的数据版本 v$currentVersion 高于当前插件支持的 v$targetVersion，" +
                    "不支持降级",
            )
        }
        if (migration == null) {
            throw PluginDataMigrationException(
                "插件 $pluginId 缺少从 v$currentVersion 到 v$targetVersion 的数据迁移",
            )
        }

        try {
            store.transaction {
                migration.migrate(storage, currentVersion, targetVersion)
                store.writeDataSchemaVersion(targetVersion)
            }
        } catch (error: PluginDataMigrationException) {
            throw error
        } catch (error: Exception) {
            throw PluginDataMigrationException(
                "插件 $pluginId 从 v$currentVersion 迁移到 v$targetVersion 失败",
                error,
            )
        }
    }
}
