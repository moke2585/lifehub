package com.lifehub.pluginapi

import kotlinx.serialization.Serializable

/**
 * 插件清单。
 *
 * 版本字段约定：
 * - [minHostVersion] / [maxHostVersion] 对应宿主 versionCode，而不是 versionName。
 * - [pluginSdkVersion] 对应 [PluginApiVersion.MAJOR]。
 * - [dataSchemaVersion] 由插件数据层使用，宿主升级时据此决定是否迁移。
 */
@Serializable
data class PluginManifest(
    val pluginId: String,
    val name: String,
    val version: String,
    val description: String = "",
    val publisher: String,
    /** 宿主图标标识，例如 ledger、memo；未识别时使用默认插件图标。 */
    val icon: String = "plugin",
    val minHostVersion: Int,
    val maxHostVersion: Int? = null,
    val pluginSdkVersion: Int,
    val capabilities: List<PluginCapability> = emptyList(),
    val entryPage: String,
    val pages: List<PluginPage> = emptyList(),
    /** 主页快捷新建入口；pageId 必须是已声明页面。 */
    val quickCreate: PluginQuickCreate? = null,
    /** 主页小组件；由宿主用固定模板渲染，未知 type 运行时忽略。 */
    val homeWidgets: List<PluginHomeWidget> = emptyList(),
    /** 全局搜索数据源；宿主以 pageId=search 调用该 sourceId。 */
    val search: PluginSearch? = null,
    val dataSchemaVersion: Int = 1,
)

@Serializable
data class PluginSearch(
    val sourceId: String,
)

@Serializable
data class PluginQuickCreate(
    val pageId: String,
    val label: String,
)

@Serializable
data class PluginCapability(
    val id: String,
    val reason: String = "",
)

@Serializable
data class PluginPage(
    val id: String,
    val title: String,
    val file: String,
)
