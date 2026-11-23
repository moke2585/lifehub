package com.lifehub.app.plugin.runtime

import com.lifehub.pluginapi.PluginRecord
import com.lifehub.pluginapi.PluginStorage
import com.lifehub.pluginapi.PluginStorageLimits

fun PluginStorage.readAllRecords(collection: String): List<PluginRecord> {
    val records = mutableListOf<PluginRecord>()
    var offset = 0
    while (true) {
        val batch = list(collection, PluginStorageLimits.MAX_PAGE_SIZE, offset)
        if (batch.isEmpty()) break
        records += batch
        offset += batch.size
        if (records.size >= PluginStorageLimits.MAX_RECORDS_PER_COLLECTION) break
    }
    return records
}

fun dataSourceKey(pluginId: String, sourceId: String): String = "$pluginId/$sourceId"
