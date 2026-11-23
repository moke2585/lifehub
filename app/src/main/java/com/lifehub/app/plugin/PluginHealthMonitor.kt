package com.lifehub.app.plugin

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件连续异常状态。超过阈值后宿主会停用插件并进入隔离状态，等待用户显式重新启用。
 */
data class PluginHealthState(
    val failureCount: Int,
    val firstFailureAt: Long,
    val quarantined: Boolean,
)

interface PluginHealthStore {
    fun read(pluginId: String): PluginHealthState?

    fun write(pluginId: String, state: PluginHealthState)

    fun clear(pluginId: String)

    fun quarantinedIds(): Set<String>
}

class SharedPreferencesPluginHealthStore(
    private val preferences: SharedPreferences,
) : PluginHealthStore {

    override fun read(pluginId: String): PluginHealthState? {
        val count = preferences.getInt(key(pluginId, SUFFIX_COUNT), 0)
        if (count <= 0) return null
        return PluginHealthState(
            failureCount = count,
            firstFailureAt = preferences.getLong(key(pluginId, SUFFIX_FIRST), 0L),
            quarantined = preferences.getBoolean(key(pluginId, SUFFIX_QUARANTINED), false),
        )
    }

    override fun write(pluginId: String, state: PluginHealthState) {
        preferences.edit {
            putInt(key(pluginId, SUFFIX_COUNT), state.failureCount)
            putLong(key(pluginId, SUFFIX_FIRST), state.firstFailureAt)
            putBoolean(key(pluginId, SUFFIX_QUARANTINED), state.quarantined)
        }
    }

    override fun clear(pluginId: String) {
        preferences.edit {
            remove(key(pluginId, SUFFIX_COUNT))
            remove(key(pluginId, SUFFIX_FIRST))
            remove(key(pluginId, SUFFIX_QUARANTINED))
        }
    }

    override fun quarantinedIds(): Set<String> =
        preferences.all.entries
            .asSequence()
            .filter { (key, value) ->
                key.startsWith(KEY_PREFIX) &&
                    key.endsWith("_$SUFFIX_QUARANTINED") &&
                    value == true
            }
            .mapNotNull { (key, _) ->
                key.removePrefix(KEY_PREFIX)
                    .removeSuffix("_$SUFFIX_QUARANTINED")
                    .ifBlank { null }
            }
            .toSet()

    private fun key(pluginId: String, suffix: String): String = "$KEY_PREFIX${pluginId}_$suffix"

    private companion object {
        const val KEY_PREFIX = "plugin_health_"
        const val SUFFIX_COUNT = "count"
        const val SUFFIX_FIRST = "first"
        const val SUFFIX_QUARANTINED = "quarantined"
    }
}

sealed interface PluginFailureResult {
    data class Recorded(val count: Int) : PluginFailureResult

    data object Quarantined : PluginFailureResult

    data object AlreadyQuarantined : PluginFailureResult
}

/**
 * 只统计处理器抛出的未预期异常；用户输入校验失败等受控 Failure 不计入崩溃阈值。
 */
class PluginHealthMonitor(
    private val store: PluginHealthStore,
    private val onQuarantine: (String) -> Unit = {},
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val failureWindowMillis: Long = DEFAULT_FAILURE_WINDOW_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _quarantinedIds = MutableStateFlow(store.quarantinedIds())
    val quarantinedIds: StateFlow<Set<String>> = _quarantinedIds.asStateFlow()

    @Synchronized
    fun recordFailure(pluginId: String): PluginFailureResult {
        val current = store.read(pluginId)
        if (current?.quarantined == true) return PluginFailureResult.AlreadyQuarantined

        val timestamp = now()
        val withinWindow = current != null &&
            timestamp - current.firstFailureAt <= failureWindowMillis
        val count = if (withinWindow) current.failureCount + 1 else 1
        val firstFailureAt = if (withinWindow) current.firstFailureAt else timestamp
        val quarantined = count >= maxFailures

        store.write(
            pluginId,
            PluginHealthState(
                failureCount = count,
                firstFailureAt = firstFailureAt,
                quarantined = quarantined,
            ),
        )

        if (quarantined) {
            _quarantinedIds.value = _quarantinedIds.value + pluginId
            onQuarantine(pluginId)
            return PluginFailureResult.Quarantined
        }
        return PluginFailureResult.Recorded(count)
    }

    @Synchronized
    fun recordSuccess(pluginId: String) {
        val current = store.read(pluginId) ?: return
        if (current.quarantined) return
        store.clear(pluginId)
    }

    /** 数据迁移失败等确定性故障可直接隔离，无需等待连续异常阈值。 */
    @Synchronized
    fun quarantine(pluginId: String) {
        if (pluginId in _quarantinedIds.value) return
        val current = store.read(pluginId)
        store.write(
            pluginId,
            PluginHealthState(
                failureCount = current?.failureCount ?: maxFailures,
                firstFailureAt = current?.firstFailureAt ?: now(),
                quarantined = true,
            ),
        )
        _quarantinedIds.value = _quarantinedIds.value + pluginId
        onQuarantine(pluginId)
    }

    /** 用户显式重新启用或卸载/重装时清除隔离与计数。 */
    @Synchronized
    fun clear(pluginId: String) {
        store.clear(pluginId)
        _quarantinedIds.value = _quarantinedIds.value - pluginId
    }

    fun isQuarantined(pluginId: String): Boolean = pluginId in _quarantinedIds.value

    private companion object {
        const val DEFAULT_MAX_FAILURES = 3
        const val DEFAULT_FAILURE_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
