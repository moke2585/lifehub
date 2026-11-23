package com.lifehub.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import java.util.Locale

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStorage(raw: String?): ThemeMode {
            val value = raw?.trim()?.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}

/** 外观偏好：默认跟随系统，可强制浅色或深色并立即持久化。 */
class ThemePreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("lifehub_theme", Context.MODE_PRIVATE)

    var mode by mutableStateOf(ThemeMode.fromStorage(prefs.getString("mode", null)))
        private set

    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", false))
        private set

    var reduceMotion by mutableStateOf(prefs.getBoolean("reduce_motion", false))
        private set

    fun select(value: ThemeMode) {
        if (mode == value) return
        mode = value
        prefs.edit { putString("mode", value.storageValue) }
    }

    fun selectDynamicColor(value: Boolean) {
        if (dynamicColor == value) return
        dynamicColor = value
        prefs.edit { putBoolean("dynamic_color", value) }
    }

    fun selectReduceMotion(value: Boolean) {
        if (reduceMotion == value) return
        reduceMotion = value
        prefs.edit { putBoolean("reduce_motion", value) }
    }
}

val LocalThemePreferenceStore = staticCompositionLocalOf<ThemePreferenceStore?> { null }
