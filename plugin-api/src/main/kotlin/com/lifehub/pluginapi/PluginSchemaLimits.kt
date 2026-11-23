package com.lifehub.pluginapi

/**
 * 页面 schema 的资源上限。宿主在加载页面时逐项校验，超过任一上限即判定插件不可用。
 */
object PluginSchemaLimits {
    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    /** 单个页面 JSON 文件的字节上限。 */
    const val MAX_PAGE_BYTES: Int = 256 * 1024

    /** 单个插件声明的页面数量上限。 */
    const val MAX_PAGES_PER_PLUGIN: Int = 50

    const val MAX_NODES_PER_PAGE: Int = 400
    const val MAX_NODE_DEPTH: Int = 6
    const val MAX_TEXT_LENGTH: Int = 2_000
    const val MAX_PAGE_TITLE_LENGTH: Int = 120
    const val MAX_LIST_ITEMS: Int = 200
    const val MAX_INPUTS_PER_PAGE: Int = 50
    const val MAX_ACTION_PARAMS: Int = 20
    const val MAX_ACTION_ID_LENGTH: Int = 128
    const val MAX_BUTTON_LABEL_LENGTH: Int = 80
    const val MAX_LIST_ITEM_TEXT_LENGTH: Int = 500
    const val MAX_CONFIRM_MESSAGE_LENGTH: Int = 1_000
    const val MAX_HOME_WIDGETS_PER_PLUGIN: Int = 6
    const val MAX_HOME_WIDGET_ID_LENGTH: Int = 64
    const val MAX_HOME_WIDGET_TITLE_LENGTH: Int = 20
    const val MAX_HOME_WIDGET_ITEMS: Int = 5
}
