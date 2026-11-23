package com.lifehub.app.plugin.runtime

import com.lifehub.pluginapi.PluginActionHandler
import com.lifehub.pluginapi.PluginPageDataHandler

/**
 * 第一方/内置插件的模块入口。
 *
 * 宿主只通过这个契约聚合 action 与 data handler，不再直接依赖具体插件类；
 * 后续外部 ZIP/APK 插件可以复用同一套 action/data 契约，由不同包源适配。
 */
interface PluginModule {
    val pluginId: String
    val actionHandlers: Map<String, PluginActionHandler>
    val dataHandlers: Map<String, PluginPageDataHandler>
}
