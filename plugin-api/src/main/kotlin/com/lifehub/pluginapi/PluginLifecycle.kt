package com.lifehub.pluginapi

/**
 * 插件生命周期事件。宿主只在状态真正发生变化时派发，重复启用/禁用不会重复回调。
 */
enum class PluginLifecycleEvent {
    INSTALL,
    ENABLE,
    DISABLE,
    UNINSTALL,
    DELETE_DATA,
}

fun interface PluginLifecycleHandler {
    fun onEvent(pluginId: String, event: PluginLifecycleEvent)
}
