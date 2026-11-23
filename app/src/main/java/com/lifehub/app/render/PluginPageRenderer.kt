package com.lifehub.app.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifehub.app.R
import com.lifehub.app.ui.theme.LifeHubElevation
import com.lifehub.app.ui.theme.LifeHubExpressiveShapes
import com.lifehub.app.ui.theme.LifeHubMotion
import com.lifehub.app.ui.theme.LifeHubSizes
import com.lifehub.app.ui.theme.LifeHubSpacing
import com.lifehub.app.ui.theme.LifeHubTheme
import com.lifehub.app.ui.theme.LifeHubType
import com.lifehub.app.ui.theme.LocalLifeHubChartColors
import com.lifehub.app.ui.theme.LocalLifeHubSemanticColors
import com.lifehub.app.ui.theme.LocalReduceMotion
import com.lifehub.app.ui.theme.rememberPressScale
import com.lifehub.pluginapi.PluginAction
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult
import com.lifehub.pluginapi.PluginButtonEmphasis
import com.lifehub.pluginapi.PluginConfirmDialog
import com.lifehub.pluginapi.PluginDataItem
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDataTone
import com.lifehub.pluginapi.PluginGeoLevel
import com.lifehub.pluginapi.PluginGeoMapData
import com.lifehub.pluginapi.PluginGeoPoint
import com.lifehub.pluginapi.PluginGeoRegion
import com.lifehub.pluginapi.PluginInputKind
import com.lifehub.pluginapi.PluginListItem
import com.lifehub.pluginapi.PluginMetric
import com.lifehub.pluginapi.PluginNode
import com.lifehub.pluginapi.PluginPageDocument
import com.lifehub.pluginapi.PluginSelectorIcons
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 页面已有周期导航时，指标卡不再重复显示同一个周期标签。 */
private val LocalMetricPeriodLabel = compositionLocalOf { true }

/**
 * 声明式页面渲染器：只认识 schema v1 的节点与动作，不执行任意 HTML、脚本或原生视图。
 */
@Composable
fun PluginPageRenderer(
    pluginId: String,
    document: PluginPageDocument,
    initialParams: Map<String, String>,
    onAction: suspend (PluginActionRequest) -> PluginActionResult,
    onNavigate: (String, Map<String, String>) -> Unit,
    onShowMessage: (String, String?, (() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    pageData: Map<String, PluginDataResult> = emptyMap(),
    onRefresh: () -> Unit = {},
    wideLayout: Boolean = false,
    /** 保存插件导出的图片，返回可展示的路径；返回 null 表示失败。 */
    onExportImage: suspend (fileName: String, pngBytes: ByteArray) -> String? = { _, _ -> null },
) {
    val defaultValues = remember(document) { document.defaultFieldValues() }
    val chipOptions = remember(document) { document.collectChipOptions() }
    val inputValues = remember(document.pageId, initialParams, chipOptions) {
        mutableStateMapOf<String, String>().apply {
            putAll(defaultValues)
            initialParams.forEach { (key, value) ->
                val allowed = chipOptions[key]
                if (allowed == null || value in allowed) {
                    put(key, value)
                }
            }
        }
    }
    val inputIds = remember(document) { document.collectFieldIds() }
    val requiredInputs = remember(document) {
        document.collectInputs().filter { it.required }
    }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    val requiredPrefix = stringResource(R.string.renderer_required_missing_prefix)
    val scope = rememberCoroutineScope()

    fun execute(action: PluginAction, params: Map<String, String>) {
        when (action) {
            is PluginAction.Host -> {
                val request = PluginActionRequest(
                    pluginId = pluginId,
                    pageId = document.pageId,
                    actionId = action.actionId,
                    params = action.params + params,
                )
                scope.launch {
                    when (val result = onAction(request)) {
                        is PluginActionResult.ShowMessage ->
                            onShowMessage(result.message, null, null)
                        is PluginActionResult.Failure ->
                            onShowMessage(result.message, null, null)
                        is PluginActionResult.Navigate -> onNavigate(result.pageId, result.params)
                        is PluginActionResult.ExportImage -> {
                            val savedPath = onExportImage(result.fileName, result.pngBytes)
                            onShowMessage(
                                savedPath?.let { "已保存到相册：$it" } ?: "导出失败，请稍后重试",
                                null,
                                null,
                            )
                        }
                        is PluginActionResult.Refresh -> {
                            result.clearInputIds.forEach { inputId ->
                                inputValues[inputId] = ""
                            }
                            result.message?.let { message ->
                                onShowMessage(
                                    message,
                                    result.undoLabel,
                                    result.undoAction?.let { undo ->
                                        { execute(undo, initialParams) }
                                    },
                                )
                            }
                            onRefresh()
                        }

                        PluginActionResult.Success -> Unit
                    }
                }
            }

            is PluginAction.Navigate -> onNavigate(action.pageId, action.params + params)
        }
    }

    fun trigger(action: PluginAction, extraParams: Map<String, String> = emptyMap()) {
        val missing = requiredInputs.filter { input ->
            inputValues[input.id].isNullOrBlank()
        }
        if (missing.isNotEmpty()) {
            onShowMessage(
                requiredPrefix + missing.joinToString("、") { it.label },
                null,
                null,
            )
            return
        }

        val params = (
            initialParams +
                inputIds.associateWith { inputValues[it].orEmpty() } +
                extraParams
            ).filterValues { it.isNotBlank() }
        val confirm = when (action) {
            is PluginAction.Host -> action.confirm
            is PluginAction.Navigate -> action.confirm
        }
        if (confirm != null) {
            pendingAction = PendingAction(action, params, confirm)
        } else {
            execute(action, params)
        }
    }

    /** 列表行/滑动动作的参数已经完整描述目标，不再叠加当前页面的初始参数。 */
    fun triggerExact(action: PluginAction) {
        val params = when (action) {
            is PluginAction.Host -> action.params
            is PluginAction.Navigate -> action.params
        }
        val confirm = when (action) {
            is PluginAction.Host -> action.confirm
            is PluginAction.Navigate -> action.confirm
        }
        if (confirm != null) {
            pendingAction = PendingAction(action, params, confirm)
        } else {
            execute(action, params)
        }
    }

    /** 行动作可覆盖当前输入值，用于编辑页点选分类等需要携带草稿的场景。 */
    fun triggerCarry(action: PluginAction) {
        val fieldParams = (initialParams + inputIds.associateWith { inputValues[it].orEmpty() })
            .filterValues { it.isNotBlank() }
        val actionParams = when (action) {
            is PluginAction.Host -> action.params
            is PluginAction.Navigate -> action.params
        }
        execute(action, fieldParams + actionParams)
    }

    // 页面自带周期导航时只显示一次周期，避免导航卡与指标卡重复。
    val showMetricPeriod = document.nodes.none { it is PluginNode.PeriodNav }
    CompositionLocalProvider(LocalMetricPeriodLabel provides showMetricPeriod) {
    val splitIndex = if (wideLayout) {
        document.nodes.indexOfFirst { it is PluginNode.RecordList }
    } else {
        -1
    }
    if (splitIndex > 0) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.xl),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.38f),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
            ) {
                document.nodes.take(splitIndex).forEach { node ->
                    RenderNode(
                        node = node,
                        inputValues = inputValues,
                        pageData = pageData,
                        onTrigger = { action -> trigger(action) },
                        onRegionTrigger = ::trigger,
                        onRowTrigger = ::triggerExact,
                        onCarryTrigger = ::triggerCarry,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.lg),
            ) {
                document.nodes.drop(splitIndex).forEach { node ->
                    RenderNode(
                        node = node,
                        inputValues = inputValues,
                        pageData = pageData,
                        onTrigger = { action -> trigger(action) },
                        onRegionTrigger = ::trigger,
                        onRowTrigger = ::triggerExact,
                        onCarryTrigger = ::triggerCarry,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.lg),
        ) {
            document.nodes.forEach { node ->
                RenderNode(
                    node = node,
                    inputValues = inputValues,
                    pageData = pageData,
                    onTrigger = { action -> trigger(action) },
                    onRegionTrigger = ::trigger,
                    onRowTrigger = ::triggerExact,
                    onCarryTrigger = ::triggerCarry,
                )
            }
        }
    }

    pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pending.confirm.title) },
            text = { Text(pending.confirm.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        execute(pending.action, pending.params)
                    },
                ) {
                    Text(pending.confirm.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(pending.confirm.cancelLabel)
                }
            },
        )
    }
    }
}

@Composable
private fun RenderNode(
    node: PluginNode,
    inputValues: MutableMap<String, String>,
    pageData: Map<String, PluginDataResult>,
    onTrigger: (PluginAction) -> Unit,
    onRegionTrigger: (PluginAction, Map<String, String>) -> Unit,
    onRowTrigger: (PluginAction) -> Unit,
    onCarryTrigger: (PluginAction) -> Unit,
) {
    when (node) {
        is PluginNode.Title -> Text(
            text = node.text,
            style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
        )

        is PluginNode.Text -> {
            // 短文本当作分区标题，长句当作说明文字，避免提示语抢走标题层级。
            val heading = node.text.length <= 12 && node.text.none { it in "。！？；…" }
            Text(
                text = node.text,
                style = if (heading) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (heading) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        is PluginNode.Button -> {
            val interactionSource = remember { MutableInteractionSource() }
            val pressScale = rememberPressScale(interactionSource, node.enabled)
            val buttonModifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
            if (node.emphasis == PluginButtonEmphasis.SECONDARY) {
                FilledTonalButton(
                    onClick = { onTrigger(node.action) },
                    modifier = buttonModifier,
                    enabled = node.enabled,
                    interactionSource = interactionSource,
                ) {
                    Text(node.label)
                }
            } else {
                Button(
                    onClick = { onTrigger(node.action) },
                    modifier = buttonModifier,
                    enabled = node.enabled,
                    interactionSource = interactionSource,
                ) {
                    Text(node.label)
                }
            }
        }

        is PluginNode.Section -> Column(
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            node.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            node.children.forEach { child ->
                RenderNode(
                    child,
                    inputValues,
                    pageData,
                    onTrigger,
                    onRegionTrigger,
                    onRowTrigger,
                    onCarryTrigger,
                )
            }
        }

        is PluginNode.ListBlock -> PluginListView(node.items)

        is PluginNode.RecordList -> PluginRecordListView(
            node = node,
            data = pageData[node.sourceId],
            onTrigger = if (node.carryInputs) onCarryTrigger else onRowTrigger,
        )

        is PluginNode.Metrics -> PluginMetricsView(
            data = pageData[node.sourceId],
        )

        is PluginNode.DateStrip -> PluginDateStrip(
            node = node,
            selected = inputValues[node.id].orEmpty(),
            onSelect = { value ->
                inputValues[node.id] = value
                node.action?.let(onTrigger)
            },
        )

        is PluginNode.DateWheel -> PluginDateWheel(
            node = node,
            value = inputValues[node.id].orEmpty(),
            onValueChange = { inputValues[node.id] = it },
        )

        is PluginNode.EmojiPicker -> PluginEmojiPicker(
            node = node,
            selected = inputValues[node.id].orEmpty(),
            onSelect = { inputValues[node.id] = it },
        )

        is PluginNode.Chips -> PluginChips(
            node = node,
            selected = inputValues[node.id].orEmpty(),
            onSelect = { value ->
                inputValues[node.id] = value
                node.action?.let(onTrigger)
            },
        )

        is PluginNode.Selector -> PluginSelector(
            node = node,
            selected = inputValues[node.id].orEmpty(),
            onSelect = { value ->
                inputValues[node.id] = value
                node.action?.let(onTrigger)
            },
        )

        is PluginNode.Row -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child ->
                Box(modifier = Modifier.weight(1f)) {
                    RenderNode(
                        child,
                        inputValues,
                        pageData,
                        onTrigger,
                        onRegionTrigger,
                        onRowTrigger,
                        onCarryTrigger,
                    )
                }
            }
        }

        is PluginNode.Rating -> PluginRating(
            node = node,
            value = inputValues[node.id]?.toIntOrNull() ?: 0,
            onSelect = { value -> inputValues[node.id] = value.toString() },
        )

        is PluginNode.PeriodNav -> PluginPeriodNav(
            node = node,
            data = pageData[node.sourceId],
            onShift = { delta ->
                val anchor = inputValues[node.anchorId].orEmpty()
                    .ifBlank { LocalDate.now().toString() }
                inputValues[node.anchorId] = anchor
                onTrigger(node.shiftAction.withParams(mapOf("delta" to delta.toString())))
            },
            onReset = {
                inputValues[node.anchorId] = LocalDate.now().toString()
                onTrigger(node.shiftAction.withParams(mapOf("delta" to "0")))
            },
        )

        is PluginNode.BarChart -> PluginBarChartView(
            node = node,
            data = pageData[node.sourceId],
            onTrigger = onTrigger,
        )

        is PluginNode.DonutChart -> PluginDonutChartView(
            node = node,
            data = pageData[node.sourceId],
        )

        is PluginNode.GeoMap -> PluginGeoMapCard(
            node = node,
            data = pageData[node.sourceId],
            onRegionTrigger = onRegionTrigger,
        )

        is PluginNode.Input -> PluginInputField(
            node = node,
            value = inputValues[node.id].orEmpty(),
            onValueChange = { inputValues[node.id] = it },
            onSubmit = node.action?.let { action -> { onTrigger(action) } },
        )

        is PluginNode.Empty -> PluginEmptyState(node)

        is PluginNode.Loading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LifeHubSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = node.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PluginInputField(
    node: PluginNode.Input,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)? = null,
) {
    val label = if (node.required) {
        stringResource(R.string.renderer_required_label, node.label)
    } else {
        node.label
    }
    val keyboardType = when (node.kind) {
        PluginInputKind.TEXT -> KeyboardType.Text
        PluginInputKind.MONEY -> KeyboardType.Decimal
        PluginInputKind.DATE -> KeyboardType.Ascii
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(node.autofocus) {
        if (node.autofocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    val canSubmit = onSubmit != null && !node.multiline

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        label = { Text(label) },
        placeholder = node.placeholder
            .takeIf { it.isNotBlank() }
            ?.let { hint -> { Text(hint) } },
        singleLine = !node.multiline,
        minLines = if (node.multiline) 4 else 1,
        maxLines = if (node.multiline) 8 else 1,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (canSubmit) ImeAction.Search else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSubmit?.invoke() },
        ),
    )
}

@Composable
private fun PluginMetricsView(data: PluginDataResult?) {
    val metrics = data?.metrics.orEmpty()
    if (metrics.isEmpty()) return

    val hero = metrics.first()
    val rest = metrics.drop(1)
    val showPeriodLabel = LocalMetricPeriodLabel.current

    PluginCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hero.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                data?.periodLabel?.takeIf { it.isNotBlank() && showPeriodLabel }?.let { label ->
                    Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = hero.value,
                style = MaterialTheme.typography.headlineLarge.merge(LifeHubType.tabularNumbers),
                fontWeight = FontWeight.SemiBold,
            )
            if (rest.isNotEmpty()) {
                BoxWithConstraints {
                    // 窄栏（平板 38% 分栏）放不下三枚金额卡时自动折成两枚一行，避免数值被截断；
                    // 宽屏下单枚指标不无限拉伸，保持紧凑的卡片节奏。
                    val gap = LifeHubSpacing.sm
                    val perRow = minOf(rest.size, (maxWidth / 104.dp).toInt().coerceAtLeast(1))
                    val tileWidth = minOf(
                        220.dp,
                        (maxWidth - gap * (perRow - 1).toFloat()) / perRow.toFloat(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        rest.chunked(perRow).forEach { rowMetrics ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                rowMetrics.forEach { metric ->
                                    MetricTile(
                                        metric = metric,
                                        modifier = Modifier.width(tileWidth),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 指标小卡：tonal 底、等宽数字与可选进度条，替代原先的分割线平铺。 */
@Composable
private fun MetricTile(
    metric: PluginMetric,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = LifeHubSpacing.md,
                vertical = LifeHubSpacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleSmall.merge(LifeHubType.tabularNumbers),
                fontWeight = FontWeight.SemiBold,
                color = if (metric.tone == PluginDataTone.NEUTRAL) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    metric.tone.metricColor()
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metric.ratio?.let { ratio ->
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = metric.tone.metricColor(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PluginDataTone.metricColor(): Color = when (this) {
    PluginDataTone.POSITIVE -> LocalLifeHubSemanticColors.current.positive
    PluginDataTone.NEGATIVE -> LocalLifeHubSemanticColors.current.negative
    PluginDataTone.NEUTRAL -> MaterialTheme.colorScheme.primary
}

/** 插件页面统一卡片：Expressive 非对称大圆角、低层表面、无描边与阴影。 */
@Composable
private fun PluginCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = LifeHubExpressiveShapes.tool,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = LifeHubElevation.card),
    ) {
        content()
    }
}

/** 空态插画：固定在插画自身的暖色底上，深浅主题都不会出现突兀的色块。 */
@Composable
private fun EmptyStateIllustration(size: Dp = 112.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(LifeHubExpressiveShapes.badge)
            .background(Color(0xFFFBF6E4)),
    ) {
        Image(
            painter = painterResource(R.drawable.illustration_empty),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PluginDateStrip(
    node: PluginNode.DateStrip,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val dates = remember(node.days, selected) {
        val recent = (0 until node.days).map { index ->
            LocalDate.now().minusDays((node.days - 1 - index).toLong())
        }
        val selectedDate = runCatching { LocalDate.parse(selected) }.getOrNull()
        if (selectedDate != null && selectedDate !in recent) {
            (recent + selectedDate).sorted()
        } else {
            recent
        }
    }
    val formatter = remember { DateTimeFormatter.ofPattern("M/d", Locale.CHINA) }
    val todayLabel = stringResource(R.string.renderer_today)
    val listState = rememberLazyListState()

    LaunchedEffect(selected, dates) {
        val index = if (selected.isBlank()) {
            0
        } else {
            dates.indexOfFirst { it.toString() == selected } + 1
        }
        if (index > 0) {
            listState.scrollToItem(index)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        ) {
            item {
                FilterChip(
                    selected = selected.isBlank(),
                    onClick = { onSelect("") },
                    label = { Text(stringResource(R.string.renderer_all_dates)) },
                )
            }
            items(dates, key = { it.toString() }) { date ->
                FilterChip(
                    selected = selected == date.toString(),
                    onClick = { onSelect(date.toString()) },
                    label = {
                        Text(
                            if (date == LocalDate.now()) todayLabel else date.format(formatter),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginEmojiPicker(
    node: PluginNode.EmojiPicker,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val collapsible = node.collapsedCount > 0 && node.options.size > node.collapsedCount
    // 当前选中项若在折叠区间内，直接展开，避免看不到已选图标。
    var expanded by remember(node.options, node.collapsedCount, selected) {
        mutableStateOf(
            collapsible && node.options.indexOf(selected) >= node.collapsedCount,
        )
    }
    val visible = if (collapsible && !expanded) {
        node.options.take(node.collapsedCount)
    } else {
        node.options
    }
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        ) {
            visible.forEach { emoji ->
                FilterChip(
                    selected = selected == emoji,
                    onClick = { onSelect(emoji) },
                    label = {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
            }
            if (collapsible) {
                FilterChip(
                    selected = false,
                    onClick = { expanded = !expanded },
                    label = {
                        Text(
                            text = if (expanded) "收起" else "更多",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(
                                if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PluginDateWheel(
    node: PluginNode.DateWheel,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selected = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull()
    }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA) }
    val remaining = selected?.let { date ->
        val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
        when {
            days < 0 -> "已逾期 ${-days} 天"
            days == 0L -> "今天截止"
            else -> "还剩 $days 天"
        }
    }

    PluginCard {
        Column(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        ) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
                ) {
                    Text(
                        text = selected?.format(formatter) ?: node.emptyLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    remaining?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { showPicker = true }) {
                    Text(if (selected == null) "选择日期" else "修改")
                }
                if (node.allowEmpty && selected != null) {
                    TextButton(onClick = { onValueChange("") }) {
                        Text("清除")
                    }
                }
            }
        }
    }

    if (showPicker) {
        DateWheelDialog(
            initial = selected ?: LocalDate.now(),
            allowEmpty = node.allowEmpty,
            onDismiss = { showPicker = false },
            onConfirm = { date ->
                showPicker = false
                onValueChange(date?.toString().orEmpty())
            },
        )
    }
}

@Composable
private fun DateWheelDialog(
    initial: LocalDate,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val years = remember(initial, today) {
        val start = minOf(today.year - 1, initial.year)
        val end = maxOf(today.year + 10, initial.year)
        (start..end).toList()
    }
    var yearIndex by remember { mutableIntStateOf(years.indexOf(initial.year).coerceAtLeast(0)) }
    var monthIndex by remember { mutableIntStateOf(initial.monthValue - 1) }
    var dayIndex by remember { mutableIntStateOf(initial.dayOfMonth - 1) }
    val year = years[yearIndex]
    val month = monthIndex + 1
    val maxDay = YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(maxDay) {
        if (dayIndex > maxDay - 1) dayIndex = maxDay - 1
    }
    val day = (dayIndex + 1).coerceAtMost(maxDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择截止日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
                Text(
                    text = "%d年%d月%d日".format(Locale.CHINA, year, month, day),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
                ) {
                    WheelColumn(
                        values = years.map { "${it}年" },
                        selectedIndex = yearIndex,
                        onSelected = { yearIndex = it },
                        modifier = Modifier.weight(1.4f),
                    )
                    WheelColumn(
                        values = (1..12).map { "${it}月" },
                        selectedIndex = monthIndex,
                        onSelected = { monthIndex = it },
                        modifier = Modifier.weight(1f),
                    )
                    key(maxDay) {
                        WheelColumn(
                            values = (1..maxDay).map { "${it}日" },
                            selectedIndex = day - 1,
                            onSelected = { dayIndex = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalDate.of(year, month, day)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                if (allowEmpty) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("不设置")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeSelected = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = safeSelected)
    val flingBehavior = rememberSnapFlingBehavior(state)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val itemHeight = 40.dp * fontScale
    val viewportHeight = 194.dp * fontScale
    val verticalPadding = (viewportHeight - itemHeight) / 2
    var ready by remember { mutableStateOf(false) }
    val centerIndex by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo
            val visible = layout.visibleItemsInfo
            if (visible.isEmpty()) {
                safeSelected
            } else {
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                visible.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                    ?: safeSelected
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        ready = true
    }
    LaunchedEffect(centerIndex, values.size, ready) {
        if (ready && centerIndex in values.indices && centerIndex != selectedIndex) {
            onSelected(centerIndex)
        }
    }

    Box(
        modifier = modifier
            .height(viewportHeight)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                ),
        )
        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(values) { index, label ->
                val distance = abs(index - centerIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = when (distance) {
                                0 -> 1f
                                1 -> 0.55f
                                else -> 0.25f
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginPeriodNav(
    node: PluginNode.PeriodNav,
    data: PluginDataResult?,
    onShift: (Int) -> Unit,
    onReset: () -> Unit,
) {
    PluginCard {
        Row(
            modifier = Modifier.padding(
                horizontal = LifeHubSpacing.md,
                vertical = LifeHubSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { onShift(-1) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.renderer_prev_period),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = data?.periodLabel?.ifBlank { null }
                        ?: stringResource(R.string.renderer_period_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = LifeHubSpacing.sm),
                ) {
                    Text(
                        text = node.resetLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            FilledTonalIconButton(onClick = { onShift(1) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.renderer_next_period),
                )
            }
        }
    }
}

@Composable
private fun PluginBarChartView(
    node: PluginNode.BarChart,
    data: PluginDataResult?,
    onTrigger: (PluginAction) -> Unit,
) {
    val bars = data?.bars.orEmpty()
    if (bars.isEmpty()) return
    val reduceMotion = LocalReduceMotion.current
    val growth = remember(bars) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(bars) {
        if (!reduceMotion) {
            growth.animateTo(1f, tween(LifeHubMotion.long))
        }
    }
    var selectedIndex by remember(bars) { mutableStateOf<Int?>(null) }
    val selectedBar = selectedIndex?.let(bars::getOrNull)
    val hint = stringResource(R.string.renderer_bar_tap_hint)
    // 月份视图有 28–31 根柱子：收窄列宽，让更多日期在首屏可见。
    val compact = bars.size > 12
    val columnWidth = when {
        bars.size > 20 -> 26.dp
        compact -> 32.dp
        else -> 48.dp
    }
    val barWidth = if (compact) 18.dp else 22.dp

    PluginCard {
        Column(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedBar?.let {
                        stringResource(R.string.renderer_bar_detail, it.label, it.value)
                    } ?: hint,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedBar != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val drilldown = node.action
                if (selectedBar != null && drilldown != null) {
                    TextButton(
                        onClick = {
                            onTrigger(
                                drilldown.withParams(
                                    buildMap {
                                        put("barLabel", selectedBar.label)
                                        put("barValue", selectedBar.value)
                                        selectedIndex?.let { put("barIndex", it.toString()) }
                                    },
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.renderer_bar_drilldown))
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
                itemsIndexed(bars) { index, bar ->
                    val selected = selectedIndex == index
                    val description = stringResource(
                        R.string.renderer_bar_detail,
                        bar.label,
                        bar.value,
                    )
                    Column(
                        modifier = Modifier
                            .width(columnWidth)
                            .height(132.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(16.dp),
                            )
                            .selectable(
                                selected = selected,
                                role = Role.Button,
                                onClick = {
                                    selectedIndex = if (selected) null else index
                                },
                            )
                            .semantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (bars.size <= 12 || selected) {
                            Text(
                                text = bar.value,
                                style = MaterialTheme.typography.labelSmall.merge(
                                    LifeHubType.tabularNumbers,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height(
                                    (8 + 72 * bar.ratio.coerceIn(0f, 1f) * growth.value).dp,
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        ),
                                    ),
                                    shape = RoundedCornerShape(
                                        topStart = 11.dp,
                                        topEnd = 11.dp,
                                        bottomEnd = 4.dp,
                                        bottomStart = 4.dp,
                                    ),
                                ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else null,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginDonutChartView(
    node: PluginNode.DonutChart,
    data: PluginDataResult?,
) {
    val segments = data?.segments.orEmpty()
    if (segments.isEmpty()) return

    val reduceMotion = LocalReduceMotion.current
    val growth = remember(segments) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(segments) {
        if (!reduceMotion) {
            growth.animateTo(1f, tween(LifeHubMotion.long))
        }
    }
    val palette = LocalLifeHubChartColors.current.series
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = data?.chartTotal.orEmpty()

    PluginCard {
        Column(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.lg),
        ) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(132.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 20.dp.toPx()
                        val inset = strokeWidth / 2
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )
                        var startAngle = -90f
                        segments.forEachIndexed { index, segment ->
                            val sweep = segment.ratio.coerceIn(0f, 1f) * 360f
                            if (sweep > 0f) {
                                val gap = if (segments.size > 1 && sweep > 6f) 2.4f else 0f
                                drawArc(
                                    color = palette[index % palette.size],
                                    startAngle = startAngle + gap / 2,
                                    sweepAngle = ((sweep - gap).coerceAtLeast(0.6f) * growth.value),
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                )
                            }
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.renderer_chart_total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = total,
                            style = MaterialTheme.typography.headlineSmall.merge(
                                LifeHubType.tabularNumbers,
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(LifeHubSpacing.lg))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
                ) {
                    segments.take(5).forEachIndexed { index, segment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = palette[index % palette.size],
                                        shape = CircleShape,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = segment.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = segment.value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${(segment.ratio * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (segments.size > 5) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                segments.drop(5).forEachIndexed { index, segment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = palette[(index + 5) % palette.size],
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
                        Text(
                            text = segment.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = segment.value,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(LifeHubSpacing.md))
                        Text(
                            text = "${(segment.ratio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginGeoMapCard(
    node: PluginNode.GeoMap,
    data: PluginDataResult?,
    onRegionTrigger: (PluginAction, Map<String, String>) -> Unit,
) {
    val map = data?.geo
    PluginCard {
        Column(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        ) {
            if (map == null) {
                if (data?.isError == true) {
                    Text(
                        text = data.emptyTitle.ifBlank { "地图加载失败" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (data.emptyMessage.isNotBlank()) {
                        Text(
                            text = data.emptyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "地图加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }
            if (map.title.isNotBlank()) {
                Text(
                    text = map.title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (map.subtitle.isNotBlank()) {
                Text(
                    text = map.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GeoMapCanvas(
                node = node,
                map = map,
                onRegionTrigger = onRegionTrigger,
            )
            GeoMapLegend(map)
        }
    }
}

@Composable
private fun GeoMapCanvas(
    node: PluginNode.GeoMap,
    map: PluginGeoMapData,
    onRegionTrigger: (PluginAction, Map<String, String>) -> Unit,
) {
    val aspect = map.aspectRatio
        .takeIf { it.isFinite() && it > 0.1f }
        ?.coerceIn(0.35f, 3.2f)
        ?: 1.3f
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = if (maxWidth > 0.dp) maxWidth else 1.dp
        val mapWidth = minOf(availableWidth, node.height.dp * aspect)
        val mapHeight = mapWidth / aspect
        val density = LocalDensity.current
        val widthPx = with(density) { mapWidth.toPx() }
        val heightPx = with(density) { mapHeight.toPx() }
        val tapRadiusPx = with(density) { 22.dp.toPx() }
        val geometry = remember(map, widthPx, heightPx, tapRadiusPx) {
            buildGeoGeometry(map, widthPx, heightPx, tapRadiusPx)
        }
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        val seaColor = MaterialTheme.colorScheme.surfaceContainerLowest
        val landColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val litColor = MaterialTheme.colorScheme.primary
        val thinLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        val provinceLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
        val labelBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        val labelColor = MaterialTheme.colorScheme.onSurface
        val mapDescription = buildString {
            append(map.title.ifBlank { "行政区地图" })
            val selectable = map.regions.count { it.interactive }
            if (selectable > 0) append("，$selectable 个可点亮区域")
            append("，点击区域查看详情")
            if (node.zoomable) append("，支持双指缩放和拖动")
        }
        val borderWidth = with(density) { 1.dp.toPx() }
        val cornerRadius = CornerRadius(with(density) { 20.dp.toPx() })
        var mapScale by remember { mutableFloatStateOf(1f) }
        var mapOffset by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(widthPx, heightPx) {
            mapOffset = clampMapOffset(mapOffset, mapScale, widthPx, heightPx)
        }
        fun applyScale(newScale: Float) {
            val clamped = newScale.coerceIn(1f, 4f)
            if (clamped <= 1.01f) {
                mapScale = 1f
                mapOffset = Offset.Zero
            } else {
                val center = Offset(widthPx / 2f, heightPx / 2f)
                val ratio = clamped / mapScale
                mapOffset = clampMapOffset(
                    center + (mapOffset - center) * ratio,
                    clamped,
                    widthPx,
                    heightPx,
                )
                mapScale = clamped
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(modifier = Modifier.size(mapWidth, mapHeight)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = mapDescription }
                    .pointerInput(geometry, node.action, node.zoomable) {
                        if (!node.zoomable) {
                            detectTapGestures { position ->
                                val action = node.action ?: return@detectTapGestures
                                val region = geometry.hitTest(position) ?: return@detectTapGestures
                                onRegionTrigger(action, region.actionParams())
                            }
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var pastTouchSlop = false
                            var didTransform = false
                            var totalPan = Offset.Zero
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                val pressedCount = event.changes.count { it.pressed }
                                val panChange = event.calculatePan()
                                if (pressedCount >= 2) {
                                    didTransform = true
                                    pastTouchSlop = true
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val zoomChange = event.calculateZoom()
                                    val newScale = (mapScale * zoomChange).coerceIn(1f, 4f)
                                    if (newScale <= 1f) {
                                        mapScale = 1f
                                        mapOffset = Offset.Zero
                                    } else {
                                        val factor = newScale / mapScale
                                        mapOffset = clampMapOffset(
                                            centroid + (mapOffset - centroid) * factor + panChange,
                                            newScale,
                                            widthPx,
                                            heightPx,
                                        )
                                        mapScale = newScale
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    totalPan += panChange
                                    if (!pastTouchSlop &&
                                        totalPan.getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        pastTouchSlop = true
                                        didTransform = true
                                    }
                                    if (pastTouchSlop && mapScale > 1f) {
                                        mapOffset = clampMapOffset(
                                            mapOffset + panChange,
                                            mapScale,
                                            widthPx,
                                            heightPx,
                                        )
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                event = awaitPointerEvent()
                            }
                            val tapAction = node.action
                            if (!didTransform && !pastTouchSlop && tapAction != null) {
                                val base = Offset(
                                    (down.position.x - mapOffset.x) / mapScale,
                                    (down.position.y - mapOffset.y) / mapScale,
                                )
                                val region = geometry.hitTest(
                                    base,
                                    tapRadiusPx / mapScale,
                                ) ?: return@awaitEachGesture
                                onRegionTrigger(tapAction, region.actionParams())
                            }
                        }
                    },
            ) {
                drawRoundRect(color = seaColor, cornerRadius = cornerRadius)
                val rounded = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, size.width, size.height),
                            cornerRadius = cornerRadius,
                        ),
                    )
                }
                clipPath(rounded) {
                    withTransform({
                        translate(mapOffset.x, mapOffset.y)
                        scale(mapScale, mapScale, pivot = Offset.Zero)
                    }) {
                        geometry.shapes.forEach { shape ->
                            val region = shape.region
                            if (region.level != PluginGeoLevel.PROVINCE && region.name.isNotBlank()) {
                                drawPath(
                                    path = shape.path,
                                    color = if (region.lit) litColor.copy(alpha = 0.86f) else landColor,
                                )
                            }
                        }
                        geometry.shapes.forEach { shape ->
                            val region = shape.region
                            if (region.level != PluginGeoLevel.PROVINCE) {
                                drawPath(
                                    path = shape.path,
                                    color = if (region.lit) litColor else thinLineColor,
                                    style = Stroke(
                                        width = if (region.lit) 1.2f.dp.toPx() else 0.7f.dp.toPx(),
                                    ),
                                )
                            }
                        }
                        geometry.shapes.forEach { shape ->
                            if (shape.region.level == PluginGeoLevel.PROVINCE) {
                                drawPath(
                                    path = shape.path,
                                    color = provinceLineColor,
                                    style = Stroke(width = 1.8f.dp.toPx()),
                                )
                            }
                        }
                    }
                }
                // 标签绘制在缩放变换之外：字号随缩放温和增大，避免放大后被文字盖住。
                val placed = mutableListOf<Rect>()
                val paddingX = 3.dp.toPx()
                val paddingY = 1.5.dp.toPx()
                geometry.labels.forEach { label ->
                    val center = Offset(
                        x = label.position.x * mapScale + mapOffset.x,
                        y = label.position.y * mapScale + mapOffset.y,
                    )
                    if (center.x < -48f || center.x > size.width + 48f ||
                        center.y < -48f || center.y > size.height + 48f
                    ) {
                        return@forEach
                    }
                    val baseSize = if (label.province) 12f else 10f
                    val zoomFactor = 1f + (mapScale - 1f) * 0.22f
                    val fontSize = (baseSize * label.areaFactor * zoomFactor).coerceIn(8.5f, 22f)
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(label.text),
                        style = labelStyle.copy(
                            color = labelColor,
                            fontSize = fontSize.sp,
                        ),
                        maxLines = 1,
                    )
                    val textWidth = layout.size.width.toFloat()
                    val textHeight = layout.size.height.toFloat()
                    if (textWidth <= 0f || textHeight <= 0f) return@forEach
                    val maxLeft = (size.width - textWidth - 2f).coerceAtLeast(2f)
                    val maxTop = (size.height - textHeight - 2f).coerceAtLeast(2f)
                    val left = (center.x - textWidth / 2f).coerceIn(2f, maxLeft)
                    val top = (center.y - textHeight / 2f).coerceIn(2f, maxTop)
                    val rect = Rect(
                        left - paddingX,
                        top - paddingY,
                        left + textWidth + paddingX,
                        top + textHeight + paddingY,
                    )
                    if (placed.any { it.overlaps(rect) }) return@forEach
                    placed += rect
                    drawRoundRect(
                        color = labelBackground,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawText(layout, topLeft = Offset(left, top))
                }
                drawRoundRect(
                    color = thinLineColor,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = borderWidth),
                )
            }
        }
        if (node.zoomable) {
            Spacer(modifier = Modifier.height(6.dp))
            GeoMapZoomSlider(
                scale = mapScale,
                onScaleChange = { applyScale(it) },
            )
        }
        }
    }
}

/** 地图外的倍率滑块：不遮挡地图内容，也避免与放大后的标签重叠。 */
@Composable
private fun GeoMapZoomSlider(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "地图缩放倍率" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
    ) {
        Text(
            text = "缩放",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = scale,
            onValueChange = onScaleChange,
            valueRange = 1f..4f,
            steps = 5,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = String.format(Locale.US, "%.1f×", scale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (scale > 1.01f) {
            TextButton(onClick = { onScaleChange(1f) }) {
                Text("复位")
            }
        }
    }
}

@Composable
private fun GeoMapLegend(map: PluginGeoMapData) {
    val hasProvince = map.regions.any { it.level == PluginGeoLevel.PROVINCE }
    val thinLevels = map.regions
        .filter { it.level != PluginGeoLevel.PROVINCE }
        .map { it.level }
        .distinct()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasProvince) {
            GeoMapLegendItem(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                thickness = 1.8.dp,
                label = "省界",
            )
        }
        if (PluginGeoLevel.COUNTY in thinLevels) {
            GeoMapLegendItem(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                thickness = 0.7.dp,
                label = "区县界",
            )
        }
        if (PluginGeoLevel.CITY in thinLevels) {
            GeoMapLegendItem(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                thickness = 0.7.dp,
                label = "市界",
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        val litCount = map.regions.count { it.lit }
        if (litCount > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = "已点亮 $litCount",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        horizontal = LifeHubSpacing.sm,
                        vertical = 4.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GeoMapLegendItem(color: Color, thickness: Dp, label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LifeHubSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = thickness.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private class GeoShape(
    val region: PluginGeoRegion,
    val path: Path,
    val bounds: Rect,
    val center: Offset,
)

private class GeoLabel(
    val text: String,
    val position: Offset,
    val priority: Int,
    /** 省名字号随面积与缩放自适应；区县固定为基础字号。 */
    val areaFactor: Float = 1f,
    val province: Boolean = false,
)

private class GeoGeometry(
    val shapes: List<GeoShape>,
    val labels: List<GeoLabel>,
    private val widthPx: Float,
    private val heightPx: Float,
    private val tapRadiusPx: Float,
) {
    private val interactiveShapes: List<GeoShape> =
        shapes.filter { it.region.interactive }

    fun hitTest(position: Offset, tapRadius: Float = tapRadiusPx): PluginGeoRegion? {
        if (widthPx <= 0f || heightPx <= 0f) return null
        val nx = position.x / widthPx
        val ny = position.y / heightPx
        interactiveShapes.asReversed().firstOrNull { shape ->
            shape.bounds.contains(position) && pointInRings(shape.region.rings, nx, ny)
        }?.let { return it.region }

        // 小区域或岛屿难以精确点中时，选择最近的区域中心。
        var nearest: GeoShape? = null
        var nearestDistance = tapRadius * tapRadius
        interactiveShapes.forEach { shape ->
            val dx = shape.center.x - position.x
            val dy = shape.center.y - position.y
            val distance = dx * dx + dy * dy
            if (distance <= nearestDistance) {
                nearestDistance = distance
                nearest = shape
            }
        }
        return nearest?.region
    }
}

private fun clampMapOffset(
    offset: Offset,
    scale: Float,
    widthPx: Float,
    heightPx: Float,
): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = widthPx * (scale - 1f)
    val maxY = heightPx * (scale - 1f)
    return Offset(
        x = offset.x.coerceIn(-maxX, 0f),
        y = offset.y.coerceIn(-maxY, 0f),
    )
}

private fun buildGeoGeometry(
    map: PluginGeoMapData,
    widthPx: Float,
    heightPx: Float,
    tapRadiusPx: Float,
): GeoGeometry {
    val shapes = ArrayList<GeoShape>(map.regions.size)
    map.regions.forEach { region ->
        val path = Path()
        path.fillType = PathFillType.EvenOdd
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        region.rings.forEach { ring ->
            if (ring.size < 3) return@forEach
            ring.forEachIndexed { index, point ->
                val x = point.x * widthPx
                val y = point.y * heightPx
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            path.close()
        }
        if (minX > maxX) {
            minX = 0f
            minY = 0f
            maxX = widthPx
            maxY = heightPx
        }
        val center = Offset(
            x = (region.labelX * widthPx).coerceIn(0f, widthPx),
            y = (region.labelY * heightPx).coerceIn(0f, heightPx),
        )
        shapes += GeoShape(
            region = region,
            path = path,
            bounds = Rect(minX, minY, maxX, maxY),
            center = center,
        )
    }
    val labels = shapes.mapNotNull { shape ->
        val region = shape.region
        val text = region.label
        if (text.isBlank()) return@mapNotNull null
        val priority = when {
            region.lit -> 2
            region.level != PluginGeoLevel.PROVINCE -> 1
            else -> 0
        }
        val area = if (widthPx > 0f && heightPx > 0f) {
            (shape.bounds.width * shape.bounds.height / (widthPx * heightPx)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val areaFactor = if (region.level == PluginGeoLevel.PROVINCE) {
            (0.9f + 0.6f * sqrt(area)).coerceIn(0.9f, 1.35f)
        } else {
            1f
        }
        GeoLabel(
            text = text,
            position = shape.center,
            priority = priority,
            areaFactor = areaFactor,
            province = region.level == PluginGeoLevel.PROVINCE,
        )
    }.sortedByDescending { it.priority }
    return GeoGeometry(
        shapes = shapes,
        labels = labels,
        widthPx = widthPx,
        heightPx = heightPx,
        tapRadiusPx = tapRadiusPx,
    )
}

private fun pointInRings(rings: List<List<PluginGeoPoint>>, x: Float, y: Float): Boolean {
    var inside = false
    rings.forEach { ring ->
        if (ring.size < 3) return@forEach
        var j = ring.lastIndex
        var i = 0
        while (i < ring.size) {
            val xi = ring[i].x
            val yi = ring[i].y
            val xj = ring[j].x
            val yj = ring[j].y
            if ((yi > y) != (yj > y) &&
                x < (xj - xi) * (y - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
            i += 1
        }
    }
    return inside
}

private fun PluginGeoRegion.actionParams(): Map<String, String> = buildMap {
    put("regionId", id)
    put("regionName", name)
    put("level", level.name.lowercase(Locale.ROOT))
    put("lit", if (lit) "1" else "0")
    if (note.isNotBlank()) put("note", note)
    if (parentId.isNotBlank()) put("parentId", parentId)
    if (parentName.isNotBlank()) put("parentName", parentName)
    if (provinceId.isNotBlank()) put("provinceId", provinceId)
    if (provinceName.isNotBlank()) put("provinceName", provinceName)
    putAll(params)
}

@Composable
private fun PluginRating(
    node: PluginNode.Rating,
    value: Int,
    onSelect: (Int) -> Unit,
) {
    val max = node.max.coerceIn(1, 5)
    val selected = value.coerceIn(0, max)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.label.ifBlank { "地区评分" },
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (selected == 0) "未评分" else "$selected 星",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
        ) {
            (1..max).forEach { star ->
                val active = star <= selected
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected == star,
                            role = Role.RadioButton,
                            onClick = { onSelect(star) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (active) R.drawable.ic_star else R.drawable.ic_star_outline,
                        ),
                        contentDescription = "$star 星",
                        tint = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginChips(
    node: PluginNode.Chips,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
            items(node.options, key = { it.value }) { option ->
                FilterChip(
                    selected = selected == option.value,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun PluginSelector(
    node: PluginNode.Selector,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = node.options.firstOrNull { it.value == selected }
        ?: node.options.firstOrNull()
    val iconRes = when (node.icon) {
        PluginSelectorIcons.FILTER -> R.drawable.ic_filter
        PluginSelectorIcons.SORT -> R.drawable.ic_sort_desc
        else -> null
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = LifeHubSpacing.md,
                vertical = LifeHubSpacing.sm,
            ),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(LifeHubSpacing.xs))
            }
            Text(
                text = current?.label ?: node.label.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = node.label,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            node.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option.value == current?.value) {
                        {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (option.value != selected) onSelect(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun PluginRecordListView(
    node: PluginNode.RecordList,
    data: PluginDataResult?,
    onTrigger: (PluginAction) -> Unit,
) {
    if (data == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LifeHubSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.renderer_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (data.items.isEmpty()) {
        PluginCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LifeHubSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
            ) {
                EmptyStateIllustration(size = 96.dp)
                Text(
                    text = data.emptyTitle.ifBlank { node.emptyTitle },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                val message = data.emptyMessage.ifBlank { node.emptyMessage }
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                node.emptyActionLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    node.emptyAction?.let { action ->
                        Button(onClick = { onTrigger(action) }) {
                            Text(label)
                        }
                    }
                }
            }
        }
        return
    }

    if (data.items.any { it.roomy }) {
        RoomyRecordList(items = data.items, onTrigger = onTrigger)
        return
    }

    val collapsible = node.collapsedCount > 0 && data.items.size > node.collapsedCount
    var expanded by remember(node.sourceId, data.items.size, node.collapsedCount) {
        mutableStateOf(false)
    }
    val visibleItems = if (collapsible && !expanded) {
        data.items.take(node.collapsedCount)
    } else {
        data.items
    }

    // 同一天的记录合并到一张卡片，日期头在卡片内部，行与行之间用分割线。
    val groups = visibleItems.groupBy { it.groupTitle }
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
        groups.forEach { (groupTitle, items) ->
            PluginCard {
                Column {
                    if (groupTitle.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = LifeHubSpacing.lg,
                                    vertical = LifeHubSpacing.md,
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = groupTitle,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            items.firstOrNull { it.groupTotal.isNotBlank() }
                                ?.let { item ->
                                    Text(
                                        text = item.groupTotal,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    items.forEachIndexed { index, item ->
                        key(item.id) {
                            RecordRow(item = item, onTrigger = onTrigger)
                        }
                        if (index != items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
        if (collapsible) {
            PluginCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(
                            horizontal = LifeHubSpacing.lg,
                            vertical = LifeHubSpacing.md,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(
                            if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(LifeHubSpacing.xs))
                    Text(
                        text = if (expanded) {
                            "收起"
                        } else {
                            "展开其余 ${data.items.size - node.collapsedCount} 个"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordRow(
    item: PluginDataItem,
    onTrigger: (PluginAction) -> Unit,
) {
    val swipeRight = item.swipeRightAction
    val swipeLeft = item.swipeLeftAction
    if (swipeRight == null && swipeLeft == null) {
        RecordRowContent(item = item, onTrigger = onTrigger)
        return
    }

    // 列表刷新后同一行可能换成另一条记录，动作必须跟随当前 item，避免误删已删除的记录。
    val currentSwipeRight by rememberUpdatedState(swipeRight)
    val currentSwipeLeft by rememberUpdatedState(swipeLeft)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val pending = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentSwipeRight
                SwipeToDismissBoxValue.EndToStart -> currentSwipeLeft
                SwipeToDismissBoxValue.Settled -> null
            }
            pending?.let { onTrigger(it.action) }
            false
        },
        // 提高触发阈值，减少误触删除/完成。
        positionalThreshold = { distance -> distance * 0.72f },
    )
    // 滑动是纯手势，TalkBack 用户需要等价的自定义动作入口。
    val accessibilityActions = buildList {
        swipeRight?.let { action ->
            add(
                CustomAccessibilityAction(action.label) {
                    onTrigger(action.action)
                    true
                },
            )
        }
        swipeLeft?.let { action ->
            add(
                CustomAccessibilityAction(action.label) {
                    onTrigger(action.action)
                    true
                },
            )
        }
    }
    val swipeModifier = if (accessibilityActions.isEmpty()) {
        Modifier
    } else {
        Modifier.semantics { customActions = accessibilityActions }
    }
    SwipeToDismissBox(
        modifier = swipeModifier,
        state = swipeState,
        enableDismissFromStartToEnd = swipeRight != null,
        enableDismissFromEndToStart = swipeLeft != null,
        backgroundContent = {
            val direction = swipeState.dismissDirection
            if (direction == SwipeToDismissBoxValue.Settled) {
                Box(Modifier.fillMaxSize())
            } else {
                val completing = direction == SwipeToDismissBoxValue.StartToEnd
                val container = if (completing) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                }
                val contentColor = if (completing) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onError
                }
                val progress = swipeState.progress.coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(container.copy(alpha = 0.55f + 0.45f * progress))
                        .padding(horizontal = LifeHubSpacing.xl),
                    horizontalArrangement = if (completing) {
                        Arrangement.Start
                    } else {
                        Arrangement.End
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(
                            if (completing) R.drawable.ic_check else R.drawable.ic_trash,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.width(LifeHubSpacing.xs))
                    Text(
                        text = if (completing) "完成" else "删除",
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        },
    ) {
        RecordRowContent(item = item, onTrigger = onTrigger)
    }
}

@Composable
private fun RoomyRecordList(
    items: List<PluginDataItem>,
    onTrigger: (PluginAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
        var currentGroup = ""
        items.forEachIndexed { index, item ->
            if (item.groupTitle.isNotBlank() && item.groupTitle != currentGroup) {
                currentGroup = item.groupTitle
                Text(
                    text = item.groupTitle,
                    modifier = Modifier.padding(
                        start = LifeHubSpacing.xs,
                        top = if (index == 0) 0.dp else LifeHubSpacing.sm,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            key(item.id) {
                PluginCard {
                    RecordRow(item = item, onTrigger = onTrigger)
                }
            }
        }
    }
}

@Composable
private fun RecordRowContent(
    item: PluginDataItem,
    onTrigger: (PluginAction) -> Unit,
) {
    val navigateAction = item.actions.firstOrNull { it.action is PluginAction.Navigate }
    val rowClickAction = item.clickAction ?: navigateAction?.action
    val extraActions = if (item.actionsInMenu) {
        emptyList()
    } else {
        item.actions.filter { it.action !== rowClickAction }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val leadingAction = item.leadingAction
    val dimmed = item.completed
    val titleColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val semanticColors = LocalLifeHubSemanticColors.current
    val trailingColor = when {
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        item.tone == PluginDataTone.POSITIVE -> semanticColors.positive
        item.tone == PluginDataTone.NEGATIVE -> semanticColors.negative
        else -> MaterialTheme.colorScheme.onSurface
    }
    val footerColor = when {
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        item.tone == PluginDataTone.NEGATIVE -> semanticColors.negative
        item.tone == PluginDataTone.POSITIVE -> semanticColors.positive
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = if (item.roomy) {
                    LifeHubSizes.listRowTriple
                } else {
                    LifeHubSizes.listRowDouble
                },
            )
            .then(
                if (rowClickAction != null) {
                    Modifier.clickable { onTrigger(rowClickAction) }
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = LifeHubSpacing.lg,
                vertical = if (item.roomy) LifeHubSpacing.lg else LifeHubSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingText.isNotBlank()) {
            Surface(
                shape = LifeHubExpressiveShapes.badge,
                color = if (dimmed) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier
                    .size(if (item.roomy) 48.dp else 40.dp)
                    .clip(LifeHubExpressiveShapes.badge)
                    .then(
                        if (leadingAction != null) {
                            Modifier.clickable(onClickLabel = leadingAction.label) {
                                onTrigger(leadingAction.action)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.leadingText,
                        style = if (item.roomy) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = if (dimmed) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(LifeHubSpacing.md))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
        ) {
            Text(
                text = item.title,
                style = if (item.roomy) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = titleColor,
                maxLines = item.titleMaxLines.coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = item.subtitleMaxLines.coerceAtLeast(1),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.footer.isNotBlank()) {
                Text(
                    text = item.footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = footerColor,
                )
            }
        }
        if (item.trailing.isNotBlank()) {
            Text(
                text = item.trailing,
                style = MaterialTheme.typography.titleSmall.merge(
                    LifeHubType.tabularNumbers,
                ),
                fontWeight = FontWeight.SemiBold,
                color = trailingColor,
            )
        }
        extraActions.forEach { itemAction ->
            TextButton(
                onClick = { onTrigger(itemAction.action) },
                contentPadding = PaddingValues(horizontal = LifeHubSpacing.sm),
            ) {
                Text(
                    text = itemAction.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (item.actionsInMenu && item.actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    item.actions.forEach { itemAction ->
                        DropdownMenuItem(
                            text = { Text(itemAction.label) },
                            onClick = {
                                menuExpanded = false
                                onTrigger(itemAction.action)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginListView(items: List<PluginListItem>) {
    PluginCard {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.renderer_list_empty),
                modifier = Modifier.padding(LifeHubSpacing.lg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = LifeHubSizes.listRowSingle)
                            .padding(horizontal = LifeHubSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (item.trailing.isNotBlank()) {
                            Text(
                                text = item.trailing,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = LifeHubSpacing.lg),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginEmptyState(node: PluginNode.Empty) {
    PluginCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifeHubSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        ) {
            EmptyStateIllustration()
            Text(
                text = node.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (node.message.isNotBlank()) {
                Text(
                    text = node.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun PluginPageDocument.collectInputs(): List<PluginNode.Input> = buildList {
    fun visit(nodes: List<PluginNode>) {
        nodes.forEach { node ->
            when (node) {
                is PluginNode.Input -> add(node)
                is PluginNode.Section -> visit(node.children)
                is PluginNode.Row -> visit(node.children)
                else -> Unit
            }
        }
    }
    visit(nodes)
}

internal fun PluginPageDocument.collectChipOptions(): Map<String, Set<String>> = buildMap {
    fun visit(nodes: List<PluginNode>) {
        nodes.forEach { node ->
            when (node) {
                is PluginNode.Chips -> put(node.id, node.options.map { it.value }.toSet())
                is PluginNode.Selector -> put(node.id, node.options.map { it.value }.toSet())
                is PluginNode.Rating -> put(
                    node.id,
                    (0..node.max).map { it.toString() }.toSet(),
                )
                is PluginNode.Section -> visit(node.children)
                is PluginNode.Row -> visit(node.children)
                else -> Unit
            }
        }
    }
    visit(nodes)
}

private fun PluginPageDocument.collectFieldIds(): List<String> = buildList {
    fun visit(nodes: List<PluginNode>) {
        nodes.forEach { node ->
            when (node) {
                is PluginNode.Input -> add(node.id)
                is PluginNode.Chips -> add(node.id)
                is PluginNode.Selector -> add(node.id)
                is PluginNode.Rating -> add(node.id)
                is PluginNode.DateStrip -> add(node.id)
                is PluginNode.DateWheel -> add(node.id)
                is PluginNode.EmojiPicker -> add(node.id)
                is PluginNode.PeriodNav -> add(node.anchorId)
                is PluginNode.Section -> visit(node.children)
                is PluginNode.Row -> visit(node.children)
                else -> Unit
            }
        }
    }
    visit(nodes)
}

private fun PluginPageDocument.defaultFieldValues(): Map<String, String> = buildMap {
    fun visit(nodes: List<PluginNode>) {
        nodes.forEach { node ->
            when (node) {
                is PluginNode.Chips -> node.defaultValue?.let { put(node.id, it) }
                is PluginNode.Selector -> node.defaultValue?.let { put(node.id, it) }
                is PluginNode.Rating -> node.defaultValue?.let { put(node.id, it) }
                is PluginNode.EmojiPicker -> node.defaultValue?.let { put(node.id, it) }
                is PluginNode.DateStrip -> if (node.defaultToday) {
                    put(node.id, LocalDate.now().toString())
                }
                is PluginNode.PeriodNav -> put(node.anchorId, LocalDate.now().toString())
                is PluginNode.Section -> visit(node.children)
                is PluginNode.Row -> visit(node.children)
                else -> Unit
            }
        }
    }
    visit(nodes)
}

private data class PendingAction(
    val action: PluginAction,
    val params: Map<String, String>,
    val confirm: PluginConfirmDialog,
)

private fun PluginAction.withParams(extra: Map<String, String>): PluginAction = when (this) {
    is PluginAction.Host -> copy(params = params + extra)
    is PluginAction.Navigate -> copy(params = params + extra)
}

private val previewDocument = PluginPageDocument(
    schemaVersion = 1,
    pageId = "home",
    title = "示例首页",
    nodes = listOf(
        PluginNode.Title("Hello Plugin", 1),
        PluginNode.Text("声明式页面预览，所有内容都由宿主组件渲染。"),
        PluginNode.Section(
            title = "输入",
            children = listOf(
                PluginNode.Input("name", "你的名字", "请输入"),
                PluginNode.Input("amount", "金额", "0.00", PluginInputKind.MONEY),
                PluginNode.Input("date", "日期", "YYYY-MM-DD", PluginInputKind.DATE),
            ),
        ),
        PluginNode.Button("打个招呼", PluginAction.Host("com.lifehub.plugin.hello.greet")),
        PluginNode.ListBlock(listOf(PluginListItem("列表项", "说明", "v1"))),
        PluginNode.Empty("空态示例", "暂无数据"),
        PluginNode.Loading("加载中"),
    ),
)

@Preview(showBackground = true, heightDp = 1_200)
@Composable
private fun PluginPageRendererPreview() {
    LifeHubTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(LifeHubSpacing.xl)
                    .verticalScroll(rememberScrollState()),
            ) {
                PluginPageRenderer(
                    pluginId = "com.lifehub.plugin.hello",
                    document = previewDocument,
                    initialParams = mapOf("name" to "预览用户"),
                    onAction = { PluginActionResult.Success },
                    onNavigate = { _, _ -> },
                    onShowMessage = { _, _, _ -> },
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1_200)
@Composable
private fun PluginPageRendererDarkPreview() {
    LifeHubTheme(darkTheme = true) {
        Surface {
            Column(
                modifier = Modifier
                    .padding(LifeHubSpacing.xl)
                    .verticalScroll(rememberScrollState()),
            ) {
                PluginPageRenderer(
                    pluginId = "com.lifehub.plugin.hello",
                    document = previewDocument,
                    initialParams = emptyMap(),
                    onAction = { PluginActionResult.Success },
                    onNavigate = { _, _ -> },
                    onShowMessage = { _, _, _ -> },
                )
            }
        }
    }
}
