package com.lifehub.app

import com.lifehub.app.capability.PluginCapabilityGrantStore
import com.lifehub.app.data.PluginDataSchemaStore
import com.lifehub.pluginapi.PluginCapabilities
import com.lifehub.pluginapi.PluginCapability
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginHomeWidget
import com.lifehub.pluginapi.PluginManifest
import com.lifehub.pluginapi.PluginPage
import com.lifehub.pluginapi.PluginRecord
import com.lifehub.pluginapi.PluginStatus
import com.lifehub.pluginapi.PluginStorage

internal const val TEST_PLUGIN_ID = "com.lifehub.plugin.hello"

internal fun testDescriptor(
    pluginId: String = TEST_PLUGIN_ID,
    status: PluginStatus = PluginStatus.ENABLED,
    capabilities: List<PluginCapability> = listOf(
        PluginCapability(PluginCapabilities.UI_ACTION, "触发操作"),
        PluginCapability(PluginCapabilities.PRIVATE_STORAGE, "保存数据"),
    ),
    homeWidgets: List<PluginHomeWidget> = emptyList(),
): PluginDescriptor {
    val manifest = PluginManifest(
        pluginId = pluginId,
        name = "示例插件",
        version = "1.0.0",
        description = "测试插件",
        publisher = "LifeHub",
        minHostVersion = 1,
        pluginSdkVersion = 1,
        capabilities = capabilities,
        entryPage = "home",
        pages = listOf(PluginPage("home", "首页", "pages/home.json")),
        homeWidgets = homeWidgets,
    )
    return PluginDescriptor(
        pluginId = pluginId,
        displayName = manifest.name,
        manifest = manifest,
        status = status,
    )
}

internal class InMemoryGrantStore : PluginCapabilityGrantStore {
    private val values = mutableMapOf<String, MutableSet<String>>()

    override fun grantedCapabilities(pluginId: String): Set<String> =
        values[pluginId]?.toSet().orEmpty()

    override fun isGranted(pluginId: String, capabilityId: String): Boolean =
        values[pluginId]?.contains(capabilityId) == true

    override fun grant(pluginId: String, capabilityIds: Set<String>) {
        values[pluginId] = capabilityIds.toMutableSet()
    }

    override fun revokeAll(pluginId: String) {
        values.remove(pluginId)
    }
}

internal class FakePluginStorage : PluginStorage, PluginDataSchemaStore {
    private val records = linkedMapOf<Pair<String, String>, PluginRecord>()
    var schemaVersion: Int? = null
        private set

    override fun insert(collection: String, payload: String, id: String?): PluginRecord {
        val recordId = id ?: "generated-${records.size + 1}"
        require(!records.containsKey(collection to recordId)) { "记录已存在" }
        val record = PluginRecord(collection, recordId, payload, 0L, 0L)
        records[collection to recordId] = record
        return record
    }

    override fun update(collection: String, id: String, payload: String): Boolean {
        val key = collection to id
        val existing = records[key] ?: return false
        records[key] = existing.copy(payload = payload, updatedAt = existing.updatedAt + 1)
        return true
    }

    override fun delete(collection: String, id: String): Boolean =
        records.remove(collection to id) != null

    override fun get(collection: String, id: String): PluginRecord? =
        records[collection to id]

    override fun list(collection: String, limit: Int, offset: Int): List<PluginRecord> =
        records.values
            .filter { it.collection == collection }
            .drop(offset)
            .take(limit)

    override fun count(collection: String): Int =
        records.values.count { it.collection == collection }

    override fun collections(): List<String> =
        records.values.map { it.collection }.distinct().sorted()

    override fun clear() {
        records.clear()
    }

    override fun readDataSchemaVersion(): Int? = schemaVersion

    override fun writeDataSchemaVersion(version: Int) {
        schemaVersion = version
    }

    override fun <T> transaction(block: () -> T): T = block()
}

internal class FakeSchemaStore(var version: Int? = null) : PluginDataSchemaStore {
    var transactions = 0

    override fun readDataSchemaVersion(): Int? = version

    override fun writeDataSchemaVersion(version: Int) {
        this.version = version
    }

    override fun <T> transaction(block: () -> T): T {
        transactions += 1
        return block()
    }
}
