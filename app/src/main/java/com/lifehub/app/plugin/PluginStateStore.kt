package com.lifehub.app.plugin

import android.content.SharedPreferences
import androidx.core.content.edit
import com.lifehub.pluginapi.PluginStatus

/**
 * 持久化用户显式操作过的状态（启用/禁用/卸载）。从未操作过的插件保持 INSTALLED，不写盘。
 */
interface PluginStateStore {
    fun read(pluginId: String): PluginStatus?

    fun write(pluginId: String, status: PluginStatus)

    fun clear(pluginId: String)
}

class SharedPreferencesPluginStateStore(
    private val preferences: SharedPreferences,
) : PluginStateStore {

    override fun read(pluginId: String): PluginStatus? =
        preferences.getString(key(pluginId), null)
            ?.let { stored -> runCatching { PluginStatus.valueOf(stored) }.getOrNull() }
            ?.takeIf {
                it == PluginStatus.ENABLED ||
                    it == PluginStatus.DISABLED ||
                    it == PluginStatus.UNINSTALLED
            }

    override fun write(pluginId: String, status: PluginStatus) {
        require(
            status == PluginStatus.ENABLED ||
                status == PluginStatus.DISABLED ||
                status == PluginStatus.UNINSTALLED,
        )
        preferences.edit { putString(key(pluginId), status.name) }
    }

    override fun clear(pluginId: String) {
        preferences.edit { remove(key(pluginId)) }
    }

    private fun key(pluginId: String): String = "plugin_state_$pluginId"
}
