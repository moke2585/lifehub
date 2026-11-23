package com.lifehub.app.plugin

import com.lifehub.app.capability.CapabilityDecision
import com.lifehub.app.capability.PluginCapabilityBroker
import com.lifehub.app.data.PluginStorageFactory
import com.lifehub.pluginapi.PluginCapabilities
import com.lifehub.pluginapi.PluginDataLimits
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginPageDataContext
import com.lifehub.pluginapi.PluginPageDataHandler
import com.lifehub.pluginapi.PluginPageDataRequest
import com.lifehub.pluginapi.PluginSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 动态数据源分发：与 action 一样经过能力代理和存储隔离，输出再经过限额校验。
 */
class PluginDataDispatcher(
    private val handlers: Map<String, PluginPageDataHandler>,
    private val capabilities: PluginCapabilityBroker,
    private val storages: PluginStorageFactory,
    private val health: PluginHealthMonitor? = null,
    private val assetReader: ((pluginId: String, path: String) -> String?)? = null,
    private val external: ((PluginPageDataRequest) -> PluginDataResult?)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(request: PluginPageDataRequest): PluginDataResult = withContext(ioDispatcher) {
        runCatching {
            when (val decision = capabilities.authorize(request.pluginId, PluginCapabilities.UI_ACTION)) {
                is CapabilityDecision.Denied -> return@withContext PluginDataResult(
                    emptyTitle = "无法加载",
                    emptyMessage = decision.reason,
                    isError = true,
                )

                CapabilityDecision.Granted -> Unit
            }

            val descriptor = capabilities.descriptor(request.pluginId)
            val handler = handlers[sourceKey(request.pluginId, request.sourceId)]
            if (handler == null) {
                if (descriptor?.source == PluginSource.EXTERNAL_APK) {
                    val result = external?.invoke(request)
                        ?: PluginDataResult(
                            emptyTitle = "无法加载",
                            emptyMessage = "插件 APK 未安装：${request.pluginId}",
                            isError = true,
                        )
                    return@withContext validate(result).also {
                        health?.recordSuccess(request.pluginId)
                    }
                }
                return@withContext PluginDataResult(
                    emptyTitle = "无法加载",
                    emptyMessage = "未注册的数据源：${request.sourceId}",
                    isError = true,
                )
            }

            val schemaVersion = descriptor
                ?.manifest
                ?.dataSchemaVersion
                ?: 1
            val storage = when (
                capabilities.authorize(request.pluginId, PluginCapabilities.PRIVATE_STORAGE)
            ) {
                CapabilityDecision.Granted ->
                    storages.forPlugin(request.pluginId, schemaVersion)

                is CapabilityDecision.Denied -> null
            }

            validate(
                handler.load(
                    PluginPageDataContext(
                        request = request,
                        storage = storage,
                        assetReader = assetReader?.let { reader ->
                            { path -> reader(request.pluginId, path) }
                        },
                    ),
                ),
            ).also { health?.recordSuccess(request.pluginId) }
        }.getOrElse { error ->
            health?.recordFailure(request.pluginId)
            PluginDataResult(
                emptyTitle = "无法加载",
                emptyMessage = error.message ?: "未知错误",
                isError = true,
            )
        }
    }

    private fun validate(result: PluginDataResult): PluginDataResult {
        if (result.items.size > PluginDataLimits.MAX_ITEMS) {
            throw IllegalArgumentException("数据源条目数量超过上限 ${PluginDataLimits.MAX_ITEMS}")
        }
        if (result.metrics.size > PluginDataLimits.MAX_METRICS) {
            throw IllegalArgumentException("数据源指标数量超过上限 ${PluginDataLimits.MAX_METRICS}")
        }
        if (result.bars.size > PluginDataLimits.MAX_BARS) {
            throw IllegalArgumentException("数据源柱状图数量超过上限 ${PluginDataLimits.MAX_BARS}")
        }
        if (result.segments.size > PluginDataLimits.MAX_SEGMENTS) {
            throw IllegalArgumentException("数据源环图分类数量超过上限 ${PluginDataLimits.MAX_SEGMENTS}")
        }
        if (result.chartTotal.length > PluginDataLimits.MAX_TEXT_LENGTH) {
            throw IllegalArgumentException("数据源图表总额过长")
        }
        result.segments.forEach { segment ->
            require(segment.label.isNotBlank()) { "环图分类缺少标签" }
            require(segment.label.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "环图分类标签过长" }
            require(segment.value.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "环图分类数值过长" }
            require(segment.ratio in 0f..1f) { "环图分类比例必须在 0..1" }
        }
        if (result.periodLabel.length > PluginDataLimits.MAX_TEXT_LENGTH) {
            throw IllegalArgumentException("数据源周期标签过长")
        }
        result.bars.forEach { bar ->
            require(bar.label.isNotBlank()) { "柱状图条目缺少标签" }
            require(bar.label.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "柱状图标签过长" }
            require(bar.value.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "柱状图数值过长" }
            require(bar.ratio in 0f..1f) { "柱状图比例必须在 0..1" }
        }
        result.metrics.forEach { metric ->
            require(metric.label.isNotBlank()) { "数据源指标缺少标签" }
            require(metric.label.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源指标标签过长" }
            require(metric.value.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源指标值过长" }
            val ratio = metric.ratio
            require(ratio == null || ratio in 0f..1f) { "数据源指标比例必须在 0..1" }
        }
        result.items.forEach { item ->
            require(item.id.isNotBlank()) { "数据源条目缺少 id" }
            require(item.title.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源条目标题过长" }
            require(item.subtitle.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源条目副标题过长" }
            require(item.trailing.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源条目尾部文本过长" }
            require(item.leadingText.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源条目图标文本过长" }
            require(item.groupTitle.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源条目分组标题过长" }
            require(item.groupTotal.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "数据源条目分组总额过长" }
            require(item.actions.size <= PluginDataLimits.MAX_ACTIONS_PER_ITEM) {
                "数据源条目动作数量超过上限 ${PluginDataLimits.MAX_ACTIONS_PER_ITEM}"
            }
            item.actions.forEach { action ->
                require(action.label.isNotBlank()) { "数据源动作缺少文案" }
                require(action.label.length <= 80) { "数据源动作文案过长" }
            }
        }
        result.geo?.let { geo ->
            require(geo.aspectRatio in 0.2f..5f) { "地图宽高比必须在 0.2..5" }
            require(geo.regions.size <= PluginDataLimits.MAX_GEO_REGIONS) {
                "地图区域数量超过上限 ${PluginDataLimits.MAX_GEO_REGIONS}"
            }
            var pointCount = 0
            geo.regions.forEach { region ->
                require(region.id.isNotBlank()) { "地图区域缺少 id" }
                require(region.name.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "地图区域名称过长" }
                require(region.label.length <= PluginDataLimits.MAX_TEXT_LENGTH) { "地图标签过长" }
                require(region.params.size <= 20) { "地图区域参数过多" }
                region.params.forEach { (key, value) ->
                    require(key.isNotBlank() && key.length <= 64) { "地图区域参数名过长" }
                    require(value.length <= PluginDataLimits.MAX_TEXT_LENGTH) {
                        "地图区域参数 $key 过长"
                    }
                }
                require(region.rings.size <= 4_000) { "地图区域多边形数量过多" }
                region.rings.forEach { ring ->
                    ring.forEach { point ->
                        require(point.x in 0f..1f && point.y in 0f..1f) {
                            "地图坐标必须位于 0..1"
                        }
                    }
                    pointCount += ring.size
                }
            }
            require(pointCount <= PluginDataLimits.MAX_GEO_POINTS) {
                "地图坐标点数量 $pointCount 超过上限 ${PluginDataLimits.MAX_GEO_POINTS}"
            }
        }
        return result
    }

    companion object {
        fun sourceKey(pluginId: String, sourceId: String): String = "$pluginId/$sourceId"
    }
}
