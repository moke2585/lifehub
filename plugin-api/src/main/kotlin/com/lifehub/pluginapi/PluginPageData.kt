package com.lifehub.pluginapi

import kotlinx.serialization.Serializable

/**
 * 动态数据源：插件页面中的 recordList 节点按 sourceId 向宿主请求内容。
 * 数据源由第一方插件注册，宿主负责能力授权、存储注入与输出限额校验。
 */
@Serializable
data class PluginPageDataRequest(
    val pluginId: String,
    val pageId: String,
    val sourceId: String,
    val params: Map<String, String> = emptyMap(),
)

data class PluginPageDataContext(
    val request: PluginPageDataRequest,
    val storage: PluginStorage? = null,
    /** 只读插件目录内的静态资源，例如内置地图数据。 */
    val assetReader: ((String) -> String?)? = null,
)

/** 归一化地图坐标，x/y 均在 0..1。 */
@Serializable
data class PluginGeoPoint(
    val x: Float,
    val y: Float,
)

@Serializable
enum class PluginGeoLevel {
    PROVINCE,
    CITY,
    COUNTY,
}

/** 地图区域：省界只描边，市/区县可点亮、可点击。 */
@Serializable
data class PluginGeoRegion(
    val id: String,
    val name: String,
    val level: PluginGeoLevel,
    val parentId: String = "",
    val parentName: String = "",
    val provinceId: String = "",
    val provinceName: String = "",
    val note: String = "",
    val lit: Boolean = false,
    val interactive: Boolean = true,
    /** 非空时在地图上绘制名称；用于省名和已点亮区域。 */
    val label: String = "",
    val labelX: Float = 0.5f,
    val labelY: Float = 0.5f,
    /** 点选该区域时附加给动作的参数，例如父级城市 id。 */
    val params: Map<String, String> = emptyMap(),
    val rings: List<List<PluginGeoPoint>> = emptyList(),
)

@Serializable
data class PluginGeoMapData(
    val title: String = "",
    val subtitle: String = "",
    /** 地图投影后的宽高比，宿主据此等比缩放。 */
    val aspectRatio: Float = 1.3f,
    val regions: List<PluginGeoRegion> = emptyList(),
)

@Serializable
data class PluginDataItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val trailing: String = "",
    val leadingText: String = "",
    val groupTitle: String = "",
    val groupTotal: String = "",
    val tone: PluginDataTone = PluginDataTone.NEUTRAL,
    /** 已完成条目由宿主置灰并排在列表底部。 */
    val completed: Boolean = false,
    /** 标题最多显示行数；宿主超出后省略。 */
    val titleMaxLines: Int = 1,
    /** 副标题最多显示行数，可包含换行。 */
    val subtitleMaxLines: Int = 1,
    /** 副标题下方的弱化小字，例如紧急程度。 */
    val footer: String = "",
    /** 使用更宽松的独立卡片布局，展示完整标题与多行信息。 */
    val roomy: Boolean = false,
    /** 点击前置图标（emoji）时执行的动作；不占用行点击，用于日常任务提升倍率等场景。 */
    val leadingAction: PluginDataAction? = null,
    /** 覆盖整行点击行为；为 null 时使用第一个 navigate 动作。 */
    val clickAction: PluginAction? = null,
    /** 右滑（开始到结束）动作；动作声明 confirm 时宿主先弹确认框，否则直接执行。 */
    val swipeRightAction: PluginDataAction? = null,
    /** 左滑（结束到开始）动作；动作声明 confirm 时宿主先弹确认框，否则直接执行。 */
    val swipeLeftAction: PluginDataAction? = null,
    val actions: List<PluginDataAction> = emptyList(),
    /** 把 [actions] 收进行尾的三点菜单，给标题与副标题留出更多空间。 */
    val actionsInMenu: Boolean = false,
)

@Serializable
enum class PluginDataTone {
    NEUTRAL,
    POSITIVE,
    NEGATIVE,
}

@Serializable
data class PluginDataAction(
    val label: String,
    val action: PluginAction,
    val emphasis: PluginButtonEmphasis = PluginButtonEmphasis.SECONDARY,
)

@Serializable
data class PluginDataResult(
    val items: List<PluginDataItem> = emptyList(),
    val metrics: List<PluginMetric> = emptyList(),
    val bars: List<PluginBar> = emptyList(),
    val segments: List<PluginSegment> = emptyList(),
    val chartTotal: String = "",
    val periodLabel: String = "",
    val emptyTitle: String = "暂无内容",
    val emptyMessage: String = "",
    /** 数据源被拒绝、缺失或执行失败；宿主据此显示重试而不是普通空态。 */
    val isError: Boolean = false,
    /** geoMap 节点使用的地理数据；与 metrics 可共用同一数据源。 */
    val geo: PluginGeoMapData? = null,
)

@Serializable
data class PluginSegment(
    val label: String,
    val value: String,
    val ratio: Float,
)

@Serializable
data class PluginBar(
    val label: String,
    val value: String,
    val ratio: Float,
)

@Serializable
data class PluginMetric(
    val label: String,
    val value: String,
    /** 0..1 的比例，用于可视化进度条；null 表示不显示比例。 */
    val ratio: Float? = null,
    /** 语义色调，宿主据此给指标着色（例如收入绿、支出红）。 */
    val tone: PluginDataTone = PluginDataTone.NEUTRAL,
)

fun interface PluginPageDataHandler {
    fun load(context: PluginPageDataContext): PluginDataResult
}

object PluginDataLimits {
    const val MAX_ITEMS: Int = 200
    const val MAX_ACTIONS_PER_ITEM: Int = 3
    const val MAX_METRICS: Int = 5
    const val MAX_BARS: Int = 31
    const val MAX_SEGMENTS: Int = 8
    const val MAX_TEXT_LENGTH: Int = 500
    const val MAX_GEO_REGIONS: Int = 500
    const val MAX_GEO_POINTS: Int = 40000
}
