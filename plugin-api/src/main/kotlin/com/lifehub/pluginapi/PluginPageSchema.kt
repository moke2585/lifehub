package com.lifehub.pluginapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 页面 schema v1。
 *
 * 节点使用 `type` 作为多态判别字段；未知节点类型会导致解析失败，由注册表转为结构化错误。
 */
@Serializable
data class PluginPageDocument(
    val schemaVersion: Int,
    val pageId: String,
    val title: String,
    val nodes: List<PluginNode> = emptyList(),
    /** 顶栏右侧的图标动作，最多 3 个，按声明顺序从右向左排列。 */
    val topBarActions: List<PluginTopBarAction> = emptyList(),
)

@Serializable
data class PluginTopBarAction(
    val icon: String,
    val contentDescription: String,
    val action: PluginAction,
)

object PluginTopBarIcons {
    const val ADD = "add"
    const val TRASH = "trash"
    const val CHART = "chart"
    const val NOTES = "notes"
    const val SEARCH = "search"
    const val LOANS = "loans"

    val ALL: Set<String> = setOf(ADD, TRASH, CHART, NOTES, SEARCH, LOANS)
}

object PluginSelectorIcons {
    const val FILTER = "filter"
    const val SORT = "sort"

    val ALL: Set<String> = setOf(FILTER, SORT)
}

@Serializable
sealed class PluginNode {

    @Serializable
    @SerialName("title")
    data class Title(
        val text: String,
        val level: Int = 1,
    ) : PluginNode()

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
    ) : PluginNode()

    @Serializable
    @SerialName("button")
    data class Button(
        val label: String,
        val action: PluginAction,
        val emphasis: PluginButtonEmphasis = PluginButtonEmphasis.PRIMARY,
        val enabled: Boolean = true,
    ) : PluginNode()

    @Serializable
    @SerialName("section")
    data class Section(
        val title: String? = null,
        val children: List<PluginNode> = emptyList(),
    ) : PluginNode()

    @Serializable
    @SerialName("list")
    data class ListBlock(
        val items: List<PluginListItem> = emptyList(),
    ) : PluginNode()

    /** 由宿主数据源动态填充的记录列表。 */
    @Serializable
    @SerialName("recordList")
    data class RecordList(
        val sourceId: String,
        val emptyTitle: String = "暂无记录",
        val emptyMessage: String = "",
        /** 空态主按钮文案；与 emptyAction 同时提供时显示。 */
        val emptyActionLabel: String? = null,
        val emptyAction: PluginAction? = null,
        /** 为 true 时行动作会合并当前页面已填写的输入值，适合在编辑页点选分类等场景。 */
        val carryInputs: Boolean = false,
        /** 大于 0 时默认只展示前 N 条，其余通过“展开”查看。 */
        val collapsedCount: Int = 0,
    ) : PluginNode()

    /** 由宿主数据源提供的指标卡；sourceId 与 recordList 可共用同一数据源。 */
    @Serializable
    @SerialName("metrics")
    data class Metrics(
        val sourceId: String,
    ) : PluginNode()

    /** 横向滑动的日期选择条，选中的值写入同名输入并触发可选 action。 */
    @Serializable
    @SerialName("dateStrip")
    data class DateStrip(
        val id: String,
        val days: Int = 14,
        val label: String = "日期",
        val action: PluginAction? = null,
        val defaultToday: Boolean = false,
    ) : PluginNode()

    /** 年/月/日滚轮日期选择；选中值以 ISO 日期写入同名输入，空值表示未设置。 */
    @Serializable
    @SerialName("dateWheel")
    data class DateWheel(
        val id: String,
        val label: String = "截止日期",
        val allowEmpty: Boolean = true,
        val emptyLabel: String = "未设置",
    ) : PluginNode()

    /** Emoji 图标选择；选中值写入同名输入。 */
    @Serializable
    @SerialName("emojiPicker")
    data class EmojiPicker(
        val id: String,
        val label: String = "图标",
        val options: List<String> = emptyList(),
        val defaultValue: String? = null,
        /** 大于 0 时默认只展示前 N 个图标，其余通过“展开”查看。 */
        val collapsedCount: Int = 0,
    ) : PluginNode()

    /** 横向快捷选择，例如收支方向或分类。 */
    @Serializable
    @SerialName("chips")
    data class Chips(
        val id: String,
        val label: String,
        val options: List<PluginChipOption>,
        val defaultValue: String? = null,
        val action: PluginAction? = null,
    ) : PluginNode()

    /** 图标 + 下拉菜单的紧凑选择器，适合筛选、排序等少量选项。 */
    @Serializable
    @SerialName("selector")
    data class Selector(
        val id: String,
        val icon: String,
        val label: String? = null,
        val options: List<PluginChipOption>,
        val defaultValue: String? = null,
        val action: PluginAction? = null,
    ) : PluginNode()

    /** 水平等宽排列子节点，用于把筛选与排序放在同一行。 */
    @Serializable
    @SerialName("row")
    data class Row(
        val children: List<PluginNode> = emptyList(),
    ) : PluginNode()

    /** 五星评分控件；选中值写入同名输入，0 表示未评分。 */
    @Serializable
    @SerialName("rating")
    data class Rating(
        val id: String,
        val label: String = "地区评分",
        val max: Int = 5,
        val defaultValue: String? = null,
    ) : PluginNode()

    /** 周期导航：上一段/下一段与当前范围标签，anchorId 保存当前锚点日期。 */
    @Serializable
    @SerialName("periodNav")
    data class PeriodNav(
        val sourceId: String,
        val anchorId: String = "anchor",
        val shiftAction: PluginAction,
        val resetLabel: String = "今天",
    ) : PluginNode()

    /** 数据源提供的柱状图；可选动作在选中柱子后由“查看明细”触发。 */
    @Serializable
    @SerialName("barChart")
    data class BarChart(
        val sourceId: String,
        val title: String = "趋势",
        val action: PluginAction? = null,
    ) : PluginNode()

    /** 数据源提供的分类环图。 */
    @Serializable
    @SerialName("donutChart")
    data class DonutChart(
        val sourceId: String,
        val title: String = "分类",
    ) : PluginNode()

    @Serializable
    @SerialName("input")
    data class Input(
        val id: String,
        val label: String,
        val placeholder: String = "",
        val kind: PluginInputKind = PluginInputKind.TEXT,
        val required: Boolean = false,
        val multiline: Boolean = false,
        /** 键盘搜索/完成时触发的动作；提供时输入框显示搜索键。 */
        val action: PluginAction? = null,
        /** 页面打开时自动聚焦，适合金额、标题等首要输入。 */
        val autofocus: Boolean = false,
    ) : PluginNode()

    /** 数据源提供的行政区划地图；点击区域时可携带区域参数触发动作。 */
    @Serializable
    @SerialName("geoMap")
    data class GeoMap(
        val sourceId: String,
        val height: Int = 480,
        /** 是否支持双指缩放与拖动；一级全国地图建议开启。 */
        val zoomable: Boolean = false,
        val action: PluginAction? = null,
    ) : PluginNode()

    @Serializable
    @SerialName("empty")
    data class Empty(
        val title: String,
        val message: String = "",
    ) : PluginNode()

    @Serializable
    @SerialName("loading")
    data class Loading(
        val text: String = "加载中…",
    ) : PluginNode()
}

@Serializable
data class PluginListItem(
    val title: String,
    val subtitle: String = "",
    val trailing: String = "",
)

@Serializable
data class PluginChipOption(
    val label: String,
    val value: String,
)

@Serializable
enum class PluginButtonEmphasis {
    @SerialName("primary")
    PRIMARY,

    @SerialName("secondary")
    SECONDARY,
}

@Serializable
enum class PluginInputKind {
    @SerialName("text")
    TEXT,

    @SerialName("money")
    MONEY,

    @SerialName("date")
    DATE,
}

/**
 * 动作绑定。`host` 交给宿主注册的 action handler，`navigate` 只允许跳到 manifest 声明的页面。
 */
@Serializable
sealed class PluginAction {

    @Serializable
    @SerialName("host")
    data class Host(
        val actionId: String,
        val params: Map<String, String> = emptyMap(),
        val confirm: PluginConfirmDialog? = null,
    ) : PluginAction()

    @Serializable
    @SerialName("navigate")
    data class Navigate(
        val pageId: String,
        val params: Map<String, String> = emptyMap(),
        val confirm: PluginConfirmDialog? = null,
    ) : PluginAction()
}

@Serializable
data class PluginConfirmDialog(
    val title: String,
    val message: String,
    val confirmLabel: String = "确定",
    val cancelLabel: String = "取消",
)
