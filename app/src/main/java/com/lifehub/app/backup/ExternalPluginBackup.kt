package com.lifehub.app.backup

/**
 * APK 插件备份桥。宿主不直接访问插件数据库，只转发 provider 导出的快照。
 */
interface ExternalPluginBackup {
    fun export(pluginId: String): String?

    fun import(pluginId: String, payload: String)

    /** 用于导入预览展示“现有 N 条”；无法获取时返回 null。 */
    fun recordCount(pluginId: String): Int?
}
