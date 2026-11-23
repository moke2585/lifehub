package com.lifehub.app.plugins

import com.lifehub.app.plugin.BuiltInPluginActions
import com.lifehub.app.plugin.runtime.PluginModule
import com.lifehub.pluginapi.PluginActionHandler
import com.lifehub.pluginapi.PluginPageDataHandler

/**
 * 第一方插件 action 与动态数据源注册表。
 * 三块插件通过 PluginModule 聚合，新增插件只需加入 modules 列表。
 */
object FirstPartyPluginHandlers {
    private val modules: List<PluginModule> = listOf(
        LedgerPlugin,
        MemoPlugin,
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
