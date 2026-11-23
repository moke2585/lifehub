package com.lifehub.app.backup

import com.lifehub.app.data.PluginDataSchemaStore
import com.lifehub.app.data.PluginRecordRules
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginDataSnapshot
import com.lifehub.pluginapi.PluginSnapshotCollection
import com.lifehub.pluginapi.PluginSnapshotRecord
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStatus
import com.lifehub.pluginapi.PluginStorage
import com.lifehub.pluginapi.PluginStorageLimits
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 版本化加密备份档案：
 * - 外层：PBKDF2 + AES-GCM 容器
 * - 内层：ZIP，包含 archive.json、data/<pluginId>.json、checksums.sha256
 *
 * 导入前会完成解密、校验和、插件存在性、数据版本与记录合法性检查；
 * 写入按插件使用事务，失败时该插件回滚到导入前状态。
 */
class PluginBackupManager(
    private val pluginProvider: () -> List<PluginDescriptor>,
    private val storageProvider: (PluginDescriptor) -> PluginStorage,
    private val appVersion: String,
    private val hostVersionCode: Int,
    private val externalBackup: ExternalPluginBackup? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now))
        return "lifehub-backup-$timestamp.lifehub.zip"
    }

    fun export(password: CharArray): ByteArray {
        val descriptors = pluginProvider()
            .filter { descriptor ->
                descriptor.manifest != null &&
                    descriptor.status != PluginStatus.ERROR &&
                    descriptor.status != PluginStatus.INCOMPATIBLE
            }
            .sortedBy { it.pluginId }
        if (descriptors.isEmpty()) {
            throw BackupException("没有可备份的插件数据")
        }
        if (descriptors.size > BackupLimits.MAX_PLUGINS) {
            throw BackupException("插件数量超过备份上限 ${BackupLimits.MAX_PLUGINS}")
        }

        val entries = linkedMapOf<String, ByteArray>()
        val pluginInfos = mutableListOf<BackupPluginInfo>()

        descriptors.forEach { descriptor ->
            val manifest = descriptor.manifest ?: return@forEach
            validatePluginId(descriptor.pluginId)

            val externalPayload = if (descriptor.source == PluginSource.EXTERNAL_APK) {
                externalBackup?.export(descriptor.pluginId)
                    ?: throw BackupException("无法从插件 APK 导出数据：${descriptor.displayName}")
            } else {
                null
            }
            val collections = if (externalPayload == null) {
                val storage = openStorage(descriptor)
                storage.collections().map { collection ->
                    BackupCollection(
                        collection = collection,
                        records = readAll(storage, collection),
                    )
                }
            } else {
                emptyList()
            }
            val recordCount = if (externalPayload == null) {
                collections.sumOf { it.records.size }
            } else {
                validateExternalSnapshot(descriptor, externalPayload).recordCount
            }

            val data = BackupPluginData(
                pluginId = descriptor.pluginId,
                dataSchemaVersion = manifest.dataSchemaVersion,
                collections = collections,
                externalPayload = externalPayload,
            )
            entries["data/${descriptor.pluginId}.json"] = encode(data)
            pluginInfos += BackupPluginInfo(
                pluginId = descriptor.pluginId,
                name = descriptor.displayName,
                pluginVersion = manifest.version,
                dataSchemaVersion = manifest.dataSchemaVersion,
                recordCount = recordCount,
            )
        }

        entries["archive.json"] = encode(
            BackupArchive(
                formatVersion = BackupLimits.FORMAT_VERSION,
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion,
                hostVersionCode = hostVersionCode,
                plugins = pluginInfos,
            ),
        )
        entries["checksums.sha256"] = buildChecksums(entries)

        val zip = buildZip(entries)
        if (zip.size > BackupLimits.MAX_ARCHIVE_BYTES) {
            throw BackupException(
                "备份内容 ${zip.size / 1024 / 1024} MB 超过上限 " +
                    "${BackupLimits.MAX_ARCHIVE_BYTES / 1024 / 1024} MB",
            )
        }
        return BackupCrypto.encrypt(zip, password)
    }

    fun preview(password: CharArray, container: ByteArray): BackupPreview {
        val plain = BackupCrypto.decrypt(container, password)
        if (plain.size > BackupLimits.MAX_ARCHIVE_BYTES) {
            throw BackupException("解压后的备份超过大小上限")
        }

        val entries = readZip(plain)
        verifyChecksums(entries)

        val archive = decode<BackupArchive>(
            entries["archive.json"] ?: throw BackupException("备份缺少 archive.json"),
        )
        if (archive.formatVersion != BackupLimits.FORMAT_VERSION) {
            throw BackupException("不支持的备份格式版本：${archive.formatVersion}")
        }
        if (archive.plugins.isEmpty()) {
            throw BackupException("备份不包含任何插件")
        }
        if (archive.plugins.size > BackupLimits.MAX_PLUGINS) {
            throw BackupException("备份中的插件数量超过上限")
        }

        val installed = pluginProvider().associateBy { it.pluginId }
        val pluginData = linkedMapOf<String, BackupPluginData>()
        val existingCounts = mutableMapOf<String, Int>()
        val seenIds = mutableSetOf<String>()

        archive.plugins.forEach { info ->
            validatePluginId(info.pluginId)
            if (!seenIds.add(info.pluginId)) {
                throw BackupException("备份包含重复插件：${info.pluginId}")
            }
            val descriptor = installed[info.pluginId]
                ?: throw BackupException("备份包含未安装的插件：${info.pluginId}")
            val manifest = descriptor.manifest
                ?: throw BackupException("插件 ${info.pluginId} 的清单不可用")
            if (descriptor.status == PluginStatus.ERROR ||
                descriptor.status == PluginStatus.INCOMPATIBLE
            ) {
                throw BackupException("插件 ${descriptor.displayName} 当前不可导入")
            }
            if (manifest.dataSchemaVersion != info.dataSchemaVersion) {
                throw BackupException(
                    "插件 ${descriptor.displayName} 的数据版本不兼容：" +
                        "备份为 v${info.dataSchemaVersion}，当前为 v${manifest.dataSchemaVersion}",
                )
            }

            val dataBytes = entries["data/${info.pluginId}.json"]
                ?: throw BackupException("备份缺少插件数据：${info.pluginId}")
            val data = decode<BackupPluginData>(dataBytes)
            if (data.pluginId != info.pluginId) {
                throw BackupException("插件数据与清单不一致：${info.pluginId}")
            }
            if (data.dataSchemaVersion != info.dataSchemaVersion) {
                throw BackupException("插件数据版本与清单不一致：${info.pluginId}")
            }
            val recordCount = if (data.externalPayload != null) {
                validateExternalSnapshot(descriptor, data.externalPayload).recordCount
            } else {
                validatePluginData(data)
                data.collections.sumOf { it.records.size }
            }
            if (recordCount != info.recordCount) {
                throw BackupException(
                    "插件 ${descriptor.displayName} 记录数不一致：" +
                        "清单 ${info.recordCount}，实际 $recordCount",
                )
            }

            pluginData[info.pluginId] = data
            existingCounts[info.pluginId] = if (descriptor.source == PluginSource.EXTERNAL_APK) {
                externalBackup?.recordCount(descriptor.pluginId) ?: 0
            } else {
                existingRecordCount(openStorage(descriptor))
            }
        }

        val expectedDataEntries = archive.plugins.map { "data/${it.pluginId}.json" }.toSet()
        val unknownDataEntries = entries.keys
            .filter { it.startsWith("data/") && it !in expectedDataEntries }
        if (unknownDataEntries.isNotEmpty()) {
            throw BackupException("备份包含未声明数据文件：${unknownDataEntries.first()}")
        }

        val descriptors = pluginData.keys.associateWith { installed.getValue(it) }
        return BackupPreview(
            archive = archive,
            pluginData = pluginData,
            descriptors = descriptors,
            existingRecordCounts = existingCounts,
        )
    }

    fun apply(preview: BackupPreview) {
        preview.pluginData.values.forEach { data ->
            val descriptor = preview.descriptors[data.pluginId]
                ?: throw BackupException("插件不存在：${data.pluginId}")

            if (descriptor.source == PluginSource.EXTERNAL_APK) {
                val payload = data.externalPayload ?: encodeSnapshot(data)
                val backup = externalBackup
                    ?: throw BackupException("插件 APK 备份桥不可用：${descriptor.displayName}")
                try {
                    backup.import(descriptor.pluginId, payload)
                } catch (error: Exception) {
                    throw BackupException(
                        "导入 ${descriptor.displayName} 失败：${error.message ?: "未知错误"}",
                        error,
                    )
                }
                return@forEach
            }

            val storage = openStorage(descriptor)
            val schemaStore = storage as? PluginDataSchemaStore
                ?: throw BackupException("存储实现不支持事务，无法安全导入")

            try {
                schemaStore.transaction {
                    storage.clear()
                    data.collections.forEach { collection ->
                        collection.records.forEach { record ->
                            storage.insert(collection.collection, record.payload, record.id)
                        }
                    }
                    schemaStore.writeDataSchemaVersion(data.dataSchemaVersion)
                }
            } catch (error: BackupException) {
                throw error
            } catch (error: Exception) {
                throw BackupException(
                    "导入 ${descriptor.displayName} 失败，已回滚该插件的数据：${error.message ?: "未知错误"}",
                    error,
                )
            }
        }
    }

    private fun readAll(storage: PluginStorage, collection: String): List<BackupRecord> {
        val records = mutableListOf<BackupRecord>()
        var offset = 0
        while (true) {
            val batch = storage.list(collection, PluginStorageLimits.MAX_PAGE_SIZE, offset)
            if (batch.isEmpty()) break
            records += batch.map { BackupRecord(id = it.id, payload = it.payload) }
            offset += batch.size
            if (records.size > PluginStorageLimits.MAX_RECORDS_PER_COLLECTION) {
                throw BackupException(
                    "集合 $collection 的记录数超过 ${PluginStorageLimits.MAX_RECORDS_PER_COLLECTION}",
                )
            }
        }
        return records
    }

    private fun existingRecordCount(storage: PluginStorage): Int =
        storage.collections().sumOf { storage.count(it) }

    private fun openStorage(descriptor: PluginDescriptor): PluginStorage =
        try {
            storageProvider(descriptor)
        } catch (error: Exception) {
            throw BackupException(
                "无法打开插件 ${descriptor.pluginId} 的数据：${error.message ?: "未知错误"}",
                error,
            )
        }

    private fun validateExternalSnapshot(
        descriptor: PluginDescriptor,
        raw: String,
    ): PluginDataSnapshot {
        if (raw.toByteArray(Charsets.UTF_8).size > BackupLimits.MAX_ARCHIVE_BYTES) {
            throw BackupException("插件 ${descriptor.displayName} 的备份数据超过大小上限")
        }
        val snapshot = try {
            json.decodeFromString<PluginDataSnapshot>(raw)
        } catch (error: Exception) {
            throw BackupException(
                "插件 ${descriptor.displayName} 的备份数据无法解析：${error.message ?: "未知错误"}",
                error,
            )
        }
        if (snapshot.pluginId != descriptor.pluginId) {
            throw BackupException("插件备份 ID 不匹配：${snapshot.pluginId}")
        }
        val manifest = descriptor.manifest
            ?: throw BackupException("插件 ${descriptor.pluginId} 的清单不可用")
        if (snapshot.dataSchemaVersion != manifest.dataSchemaVersion) {
            throw BackupException(
                "插件 ${descriptor.displayName} 的数据版本不兼容：" +
                    "备份为 v${snapshot.dataSchemaVersion}，当前为 v${manifest.dataSchemaVersion}",
            )
        }

        try {
            val collectionNames = mutableSetOf<String>()
            snapshot.collections.forEach { collection ->
                PluginRecordRules.requireCollection(collection.collection)
                if (!collectionNames.add(collection.collection)) {
                    throw BackupException("集合重复：${collection.collection}")
                }
                if (collection.records.size > PluginStorageLimits.MAX_RECORDS_PER_COLLECTION) {
                    throw BackupException("集合 ${collection.collection} 记录数超过上限")
                }
                val recordIds = mutableSetOf<String>()
                collection.records.forEach { record ->
                    PluginRecordRules.requireId(record.id)
                    PluginRecordRules.requirePayload(record.payload)
                    if (!recordIds.add(record.id)) {
                        throw BackupException(
                            "集合 ${collection.collection} 存在重复记录：${record.id}",
                        )
                    }
                }
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw BackupException("备份记录不合法：${error.message ?: "未知错误"}", error)
        }
        return snapshot
    }

    private fun encodeSnapshot(data: BackupPluginData): String =
        encode(
            PluginDataSnapshot(
                pluginId = data.pluginId,
                dataSchemaVersion = data.dataSchemaVersion,
                collections = data.collections.map { collection ->
                    PluginSnapshotCollection(
                        collection = collection.collection,
                        records = collection.records.map { record ->
                            PluginSnapshotRecord(id = record.id, payload = record.payload)
                        },
                    )
                },
            ),
        ).toString(Charsets.UTF_8)

    private fun validatePluginData(data: BackupPluginData) {
        try {
            val collectionNames = mutableSetOf<String>()
            data.collections.forEach { collection ->
                PluginRecordRules.requireCollection(collection.collection)
                if (!collectionNames.add(collection.collection)) {
                    throw BackupException("集合重复：${collection.collection}")
                }
                if (collection.records.size > PluginStorageLimits.MAX_RECORDS_PER_COLLECTION) {
                    throw BackupException("集合 ${collection.collection} 记录数超过上限")
                }
                val recordIds = mutableSetOf<String>()
                collection.records.forEach { record ->
                    PluginRecordRules.requireId(record.id)
                    PluginRecordRules.requirePayload(record.payload)
                    if (!recordIds.add(record.id)) {
                        throw BackupException(
                            "集合 ${collection.collection} 存在重复记录：${record.id}",
                        )
                    }
                }
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw BackupException("备份记录不合法：${error.message ?: "未知错误"}", error)
        }
    }

    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun readZip(data: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (!entry.isDirectory) {
                        validateEntryName(entry.name)
                        if (entries.containsKey(entry.name)) {
                            throw BackupException("备份档案包含重复文件：${entry.name}")
                        }
                        val bytes = readLimited(zip)
                        total += bytes.size
                        if (total > BackupLimits.MAX_ARCHIVE_BYTES) {
                            throw BackupException("备份解压后超过大小上限")
                        }
                        entries[entry.name] = bytes
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        return entries
    }

    private fun readLimited(input: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > BackupLimits.MAX_ARCHIVE_BYTES) {
                throw BackupException("备份条目超过大小上限")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun buildChecksums(entries: Map<String, ByteArray>): ByteArray =
        entries.entries
            .joinToString(separator = "\n") { (name, bytes) -> "${sha256(bytes)}  $name" }
            .toByteArray(Charsets.UTF_8)

    private fun verifyChecksums(entries: Map<String, ByteArray>) {
        val checksumBytes = entries["checksums.sha256"]
            ?: throw BackupException("备份缺少校验和文件")
        val expected = linkedMapOf<String, String>()
        checksumBytes.toString(Charsets.UTF_8)
            .lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parts = line.split("  ", limit = 2)
                if (parts.size != 2) {
                    throw BackupException("校验和格式不合法")
                }
                if (expected.put(parts[1], parts[0]) != null) {
                    throw BackupException("校验和包含重复条目：${parts[1]}")
                }
            }

        val actualNames = entries.keys.filter { it != "checksums.sha256" }.toSet()
        if (expected.keys != actualNames) {
            throw BackupException("备份文件清单与校验和不一致")
        }
        expected.forEach { (name, expectedHash) ->
            val actualHash = sha256(entries.getValue(name))
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                throw BackupException("备份文件校验失败：$name")
            }
        }
    }

    private fun validateEntryName(name: String) {
        if (name.isBlank() ||
            name.startsWith("/") ||
            name.contains('\\') ||
            name.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            throw BackupException("备份包含非法路径：$name")
        }
        if (name != "archive.json" && name != "checksums.sha256" && !name.startsWith("data/")) {
            throw BackupException("备份包含未知文件：$name")
        }
    }

    private fun validatePluginId(pluginId: String) {
        if (pluginId.isBlank() ||
            pluginId.startsWith("/") ||
            pluginId.contains('/') ||
            pluginId.contains('\\') ||
            pluginId.contains("..")
        ) {
            throw BackupException("插件 ID 不合法：$pluginId")
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                "%02x".format(Locale.ROOT, value)
            }

    private inline fun <reified T> encode(value: T): ByteArray =
        json.encodeToString(value).toByteArray(Charsets.UTF_8)

    private inline fun <reified T> decode(bytes: ByteArray): T =
        try {
            json.decodeFromString<T>(bytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            throw BackupException("备份 JSON 无法解析：${error.message ?: "未知错误"}", error)
        }
}
