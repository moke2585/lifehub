package com.lifehub.pluginapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 插件向宿主主页贡献的小组件描述。
 *
 * 插件只声明"展示什么"和"数据从哪来"，具体布局、主题、加载状态和错误回退由宿主负责。
 * [type] 使用字符串而不是枚举，便于旧宿主忽略未知类型而不拒绝整个插件。
 */
@Serializable
data class PluginHomeWidget(
    val id: String,
    val type: String,
    val title: String,
    val size: PluginHomeWidgetSize = PluginHomeWidgetSize.SMALL,
    /** 0..100，仅用于默认排序；用户排序优先。 */
    val priority: Int = 0,
    /** 数据类小组件必须提供；对应宿主注册的 PluginPageDataHandler sourceId。 */
    val sourceId: String? = null,
    val params: Map<String, String> = emptyMap(),
    /** 点击整个小组件的动作；v1 只允许 navigate。 */
    val action: PluginAction? = null,
    /** 金额等敏感数据，宿主可提供隐藏金额开关。 */
    val sensitive: Boolean = false,
    /** list 类型最多展示条数，默认 3，上限 5。 */
    val maxItems: Int = 3,
)

@Serializable
enum class PluginHomeWidgetSize {
    @SerialName("small")
    SMALL,

    @SerialName("medium")
    MEDIUM,

    @SerialName("large")
    LARGE,
}

object PluginHomeWidgetTypes {
    const val SUMMARY: String = "summary"
    const val PROGRESS: String = "progress"
    const val LIST: String = "list"
    const val QUICK_ACTIONS: String = "quick_actions"

    val supported: Set<String> = setOf(SUMMARY, PROGRESS, LIST, QUICK_ACTIONS)
    val dataTypes: Set<String> = setOf(SUMMARY, PROGRESS, LIST)
}
