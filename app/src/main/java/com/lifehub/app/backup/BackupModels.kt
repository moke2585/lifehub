package com.lifehub.app.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupArchive(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val hostVersionCode: Int,
    val plugins: List<BackupPluginInfo>,
)

@Serializable
data class BackupPluginInfo(
    val pluginId: String,
    val name: String,
    val pluginVersion: String,
    val dataSchemaVersion: Int,
    val recordCount: Int,
)

@Serializable
data class BackupPluginData(
    val pluginId: String,
    val dataSchemaVersion: Int,
    val collections: List<BackupCollection>,
)

@Serializable
data class BackupCollection(
    val collection: String,
    val records: List<BackupRecord>,
)

@Serializable
data class BackupRecord(
    val id: String,
    val payload: String,
)

/**
 * 导入预览：已经完成解密、校验和 schema 兼容检查，等待用户确认覆盖写入。
 */
data class BackupPreview(
    val archive: BackupArchive,
    val pluginData: Map<String, BackupPluginData>,
    val descriptors: Map<String, com.lifehub.pluginapi.PluginDescriptor>,
    val existingRecordCounts: Map<String, Int>,
)

object BackupLimits {
    const val FORMAT_VERSION: Int = 1
    const val CONTAINER_VERSION: Int = 1
    const val MAX_ARCHIVE_BYTES: Int = 50 * 1024 * 1024
    const val MAX_PLUGINS: Int = 100
    const val MIN_PASSWORD_LENGTH: Int = 8
    const val PBKDF2_ITERATIONS: Int = 600_000
    const val SALT_BYTES: Int = 16
    const val IV_BYTES: Int = 12
    const val KEY_BITS: Int = 256
    const val GCM_TAG_BITS: Int = 128
}

class BackupException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
