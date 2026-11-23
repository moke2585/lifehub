package com.lifehub.app.ui

import com.lifehub.pluginapi.PluginDataItem
import com.lifehub.pluginapi.PluginDescriptor

/** 标题/名称命中权重高于副标题与描述。 */
internal fun relevanceScore(text: String, keyword: String): Int {
    if (keyword.isBlank() || text.isBlank()) return 0
    val target = text.trim()
    return when {
        target.equals(keyword, ignoreCase = true) -> 4
        target.startsWith(keyword, ignoreCase = true) -> 3
        target.contains(keyword, ignoreCase = true) -> 2
        else -> 0
    }
}

internal fun recordRelevance(item: PluginDataItem, keyword: String): Int =
    maxOf(
        relevanceScore(item.title, keyword) * 2,
        relevanceScore(item.subtitle, keyword),
    )

internal fun pluginRelevance(descriptor: PluginDescriptor, keyword: String): Int =
    maxOf(
        relevanceScore(descriptor.displayName, keyword) * 2,
        relevanceScore(descriptor.manifest?.description.orEmpty(), keyword),
    )
