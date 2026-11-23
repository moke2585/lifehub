package com.lifehub.pluginapi

/**
 * 首版支持的插件能力。manifest 中声明未知能力会被判定为不兼容。
 */
object PluginCapabilities {
    /** 触发宿主页面中的 action。 */
    const val UI_ACTION: String = "ui.action"

    /** 读写该插件自己的隔离数据。 */
    const val PRIVATE_STORAGE: String = "storage.private"

    val supported: Set<String> = setOf(UI_ACTION, PRIVATE_STORAGE)
}
