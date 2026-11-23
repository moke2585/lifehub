package com.lifehub.pluginapi

import kotlinx.serialization.Serializable

/**
 * APK 插件与宿主之间传递的数据快照，也用于备份导入桥。
 *
 * 结构与应用内备份的 `BackupPluginData` 对齐；APK 插件不依赖宿主模块，
 * 因此把这份通用结构放在 plugin-api。
 */
@Serializable
data class PluginDataSnapshot(
    val pluginId: String,
    val dataSchemaVersion: Int,
    val collections: List<PluginSnapshotCollection> = emptyList(),
) {
    val recordCount: Int get() = collections.sumOf { it.records.size }
}

@Serializable
data class PluginSnapshotCollection(
    val collection: String,
    val records: List<PluginSnapshotRecord> = emptyList(),
)

@Serializable
data class PluginSnapshotRecord(
    val id: String,
    val payload: String,
)
