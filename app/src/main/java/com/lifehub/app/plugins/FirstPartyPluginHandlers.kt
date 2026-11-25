package com.lifehub.app.plugins

import com.lifehub.app.plugin.BuiltInPluginActions
import com.lifehub.app.plugin.runtime.PluginModule
import com.lifehub.pluginapi.PluginActionHandler
import com.lifehub.pluginapi.PluginPageDataHandler

/**
 * 第一方内置插件 action 与动态数据源注册表。
 * 任务清单与记账已迁移为独立 APK 插件，不再在宿主内注册。
 */
object FirstPartyPluginHandlers {
    private val modules: List<PluginModule> = listOf(
        TravelPlugin,
    )

    val actionHandlers: Map<String, PluginActionHandler> = modules.fold(
        BuiltInPluginActions.handlers,
    ) { handlers, module ->
        handlers + module.actionHandlers
    }

    val dataHandlers: Map<String, PluginPageDataHandler> = modules.fold(
        emptyMap<String, PluginPageDataHandler>(),
    ) { handlers, module ->
        handlers + module.dataHandlers
    }
}
