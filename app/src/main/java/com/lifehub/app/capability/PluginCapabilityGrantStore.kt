package com.lifehub.app.capability

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 用户对插件能力（capability）的显式授权。启用插件前由宿主展示能力清单并写入。
 * 禁用插件时撤销全部授权，重新启用需要再次确认。
 */
interface PluginCapabilityGrantStore {
    fun grantedCapabilities(pluginId: String): Set<String>

    fun isGranted(pluginId: String, capabilityId: String): Boolean

    fun grant(pluginId: String, capabilityIds: Set<String>)

    fun revokeAll(pluginId: String)
}

class SharedPreferencesCapabilityGrantStore(
    private val preferences: SharedPreferences,
) : PluginCapabilityGrantStore {

    override fun grantedCapabilities(pluginId: String): Set<String> =
        preferences.getStringSet(key(pluginId), emptySet())?.toSet().orEmpty()

    override fun isGranted(pluginId: String, capabilityId: String): Boolean =
        capabilityId in grantedCapabilities(pluginId)

    override fun grant(pluginId: String, capabilityIds: Set<String>) {
        if (capabilityIds.isEmpty()) {
            revokeAll(pluginId)
            return
        }
        preferences.edit { putStringSet(key(pluginId), capabilityIds.toSet()) }
    }

    override fun revokeAll(pluginId: String) {
        preferences.edit { remove(key(pluginId)) }
    }

    private fun key(pluginId: String): String = "plugin_capabilities_$pluginId"
}
