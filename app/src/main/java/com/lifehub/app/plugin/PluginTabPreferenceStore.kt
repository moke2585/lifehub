package com.lifehub.app.plugin

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 用户选择哪些已启用插件出现在底部导航栏。禁用/卸载的插件即使保留偏好也不会显示。
 */
interface PluginTabPreferenceStore {
    fun standalonePluginIds(): Set<String>

    fun isStandalone(pluginId: String): Boolean

    fun setStandalone(pluginId: String, standalone: Boolean)
}

class SharedPreferencesPluginTabPreferenceStore(
    private val preferences: SharedPreferences,
) : PluginTabPreferenceStore {

    override fun standalonePluginIds(): Set<String> =
        preferences.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    override fun isStandalone(pluginId: String): Boolean =
        pluginId in standalonePluginIds()

    override fun setStandalone(pluginId: String, standalone: Boolean) {
        val current = standalonePluginIds().toMutableSet()
        if (standalone) {
            current += pluginId
        } else {
            current -= pluginId
        }
        preferences.edit { putStringSet(KEY, current) }
    }

    private companion object {
        const val KEY = "standalone_plugins"
    }
}
