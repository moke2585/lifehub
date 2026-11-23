package com.lifehub.app.plugins.testing

import com.lifehub.pluginapi.PluginRecord
import com.lifehub.pluginapi.PluginStorage

/** 插件模块单元测试使用的内存存储；不依赖 app 模块的测试工具。 */
class FakePluginStorage : PluginStorage {
    private val records = linkedMapOf<Pair<String, String>, PluginRecord>()

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
}
