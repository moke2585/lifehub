package com.lifehub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifehub.app.R
import com.lifehub.app.ui.theme.LifeHubElevation
import com.lifehub.app.ui.theme.LifeHubSizes
import com.lifehub.app.ui.theme.LifeHubSpacing
import com.lifehub.app.ui.theme.LifeHubType
import com.lifehub.app.ui.theme.LocalLifeHubSemanticColors
import com.lifehub.pluginapi.PluginAction
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDataTone
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginHomeWidget
import com.lifehub.pluginapi.PluginHomeWidgetSize
import com.lifehub.pluginapi.PluginHomeWidgetTypes
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal const val MAX_VISIBLE_HOME_WIDGETS = 3

internal data class HomeWidgetRef(
    val descriptor: PluginDescriptor,
    val widget: PluginHomeWidget,
) {
    val key: String = "${descriptor.pluginId}/${widget.id}"
}

/**
 * 从已启用插件的 manifest 里选出默认展示的小组件。
 * 只做通用筛选，不包含任何具体插件的业务分支。
 */
internal fun availableHomeWidgets(plugins: List<PluginDescriptor>): List<HomeWidgetRef> =
    plugins.asSequence()
        .filter { it.status == PluginStatus.ENABLED && it.manifest != null }
        .flatMap { descriptor ->
            descriptor.manifest?.homeWidgets.orEmpty().asSequence().mapIndexed { index, widget ->
                Triple(index, descriptor, widget)
            }
        }
        .filter { (_, _, widget) ->
            widget.type in PluginHomeWidgetTypes.supported &&
                (widget.type !in PluginHomeWidgetTypes.dataTypes || !widget.sourceId.isNullOrBlank())
        }
        .sortedWith(
            compareByDescending<Triple<Int, PluginDescriptor, PluginHomeWidget>> {
                it.third.priority
            }
                .thenBy { it.second.pluginId }
                .thenBy { it.first },
        )
        .map { (_, descriptor, widget) -> HomeWidgetRef(descriptor, widget) }
        .toList()

internal fun buildHomeWidgets(plugins: List<PluginDescriptor>): List<HomeWidgetRef> =
    availableHomeWidgets(plugins).take(MAX_VISIBLE_HOME_WIDGETS)

internal data class HomeWidgetPreferences(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

/** 按用户顺序排列全部可用小组件；未知或已移除的 key 会被忽略。 */
internal fun orderHomeWidgets(
    available: List<HomeWidgetRef>,
    preferences: HomeWidgetPreferences,
): List<HomeWidgetRef> {
    if (preferences.order.isEmpty()) return available
    val byKey = available.associateBy { it.key }
    val ordered = preferences.order.mapNotNull { byKey[it] }
    val known = ordered.mapTo(mutableSetOf()) { it.key }
    return ordered + available.filter { it.key !in known }
}

/** 过滤隐藏项后最多展示 [MAX_VISIBLE_HOME_WIDGETS] 个。 */
internal fun visibleHomeWidgets(
    ordered: List<HomeWidgetRef>,
    preferences: HomeWidgetPreferences,
): List<HomeWidgetRef> = ordered
    .filterNot { it.key in preferences.hidden }
    .take(MAX_VISIBLE_HOME_WIDGETS)

internal fun decodeWidgetKeys(raw: String?): Set<String> =
    raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotBlank() }.toSet()

@Composable
internal fun HomeWidgetSection(
    widgets: List<HomeWidgetRef>,
    loadData: suspend (String, String, Map<String, String>) -> PluginDataResult,
    onOpen: (String, String, Map<String, String>) -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_section_today),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            if (onEdit != null) {
                TextButton(onClick = onEdit) {
                    Text(
                        text = stringResource(R.string.home_widget_edit),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (widgets.isEmpty()) {
            Text(
                text = stringResource(R.string.home_widget_all_hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        // 平板宽屏最多三列，其余保持两列；大尺寸小组件独占一行。
        val windowWidth = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width.toDp()
        }
        val columns = if (windowWidth >= 1200.dp) 3 else 2
        var index = 0
        while (index < widgets.size) {
            val row = mutableListOf<HomeWidgetRef>()
            while (
                index < widgets.size &&
                row.size < columns &&
                widgets[index].widget.size == PluginHomeWidgetSize.SMALL
            ) {
                row += widgets[index]
                index += 1
            }
            if (row.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
                    row.forEach { ref ->
                        HomeWidgetCard(
                            ref = ref,
                            loadData = loadData,
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (row.size == 1) {
                HomeWidgetCard(
                    ref = row.single(),
                    loadData = loadData,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                HomeWidgetCard(
                    ref = widgets[index],
                    loadData = loadData,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
                index += 1
            }
        }
    }
}

@Composable
internal fun HomeWidgetEditorDialog(
    widgets: List<HomeWidgetRef>,
    hidden: Set<String>,
    visibleCount: Int,
    onMove: (Int, Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_widget_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
                Text(
                    text = if (visibleCount >= MAX_VISIBLE_HOME_WIDGETS) {
                        stringResource(R.string.home_widget_editor_full)
                    } else {
                        stringResource(R.string.home_widget_editor_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    widgets.forEachIndexed { index, ref ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ref.widget.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = ref.descriptor.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onMove(index, -1) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_up),
                                    contentDescription = stringResource(R.string.home_widget_move_up),
                                )
                            }
                            IconButton(
                                onClick = { onMove(index, 1) },
                                enabled = index < widgets.lastIndex,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_down),
                                    contentDescription = stringResource(R.string.home_widget_move_down),
                                )
                            }
                            Switch(
                                checked = ref.key !in hidden,
                                onCheckedChange = { visible -> onToggle(ref.key, visible) },
                                enabled = ref.key !in hidden ||
                                    visibleCount < MAX_VISIBLE_HOME_WIDGETS,
                                modifier = Modifier.semantics {
                                    contentDescription = ref.widget.title
                                },
                            )
                        }
                        if (index != widgets.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_widget_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.home_widget_restore))
            }
        },
    )
}

private sealed interface HomeWidgetState {
    data object Loading : HomeWidgetState
    data class Ready(val data: PluginDataResult) : HomeWidgetState
    data class Failed(val message: String) : HomeWidgetState
}

@Composable
private fun HomeWidgetCard(
    ref: HomeWidgetRef,
    loadData: suspend (String, String, Map<String, String>) -> PluginDataResult,
    onOpen: (String, String, Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val widget = ref.widget
    val manifest = ref.descriptor.manifest
    var state by remember(ref) { mutableStateOf<HomeWidgetState>(HomeWidgetState.Loading) }
    var attempt by remember(ref) { mutableIntStateOf(0) }
    var masked by rememberSaveable(ref.key) { mutableStateOf(false) }

    LaunchedEffect(ref, attempt) {
        state = if (widget.type == PluginHomeWidgetTypes.QUICK_ACTIONS) {
            HomeWidgetState.Ready(PluginDataResult())
        } else {
            val sourceId = widget.sourceId
            if (sourceId.isNullOrBlank()) {
                HomeWidgetState.Failed("小组件缺少数据源")
            } else {
                try {
                    val data = withTimeoutOrNull(3_000) {
                        loadData(ref.descriptor.pluginId, sourceId, widget.params)
                    }
                    when {
                        data == null -> HomeWidgetState.Failed("加载超时")
                        data.isError -> HomeWidgetState.Failed(
                            data.emptyMessage.ifBlank { "无法加载" },
                        )

                        else -> HomeWidgetState.Ready(data)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    HomeWidgetState.Failed(error.message ?: "无法加载")
                }
            }
        }
    }

    val openWidget: () -> Unit = {
        val action = widget.action
        if (action is PluginAction.Navigate) {
            onOpen(ref.descriptor.pluginId, action.pageId, action.params)
        } else {
            onOpen(ref.descriptor.pluginId, manifest?.entryPage.orEmpty(), emptyMap())
        }
    }
    val openPage: (String, Map<String, String>) -> Unit = { pageId, params ->
        onOpen(ref.descriptor.pluginId, pageId, params)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = openWidget),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = LifeHubElevation.card),
    ) {
        Column(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(pluginIconRes(manifest?.icon)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(LifeHubSizes.iconSmall),
                )
                Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
                Text(
                    text = widget.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (widget.sensitive && state is HomeWidgetState.Ready) {
                    TextButton(
                        onClick = { masked = !masked },
                        contentPadding = PaddingValues(horizontal = LifeHubSpacing.xs, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (masked) {
                                    R.string.home_widget_show_amount
                                } else {
                                    R.string.home_widget_hide_amount
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            when (val current = state) {
                HomeWidgetState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.home_widget_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is HomeWidgetState.Failed -> Column(
                    verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.home_widget_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { attempt += 1 },
                        contentPadding = PaddingValues(horizontal = LifeHubSpacing.xs, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_widget_retry),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                is HomeWidgetState.Ready -> HomeWidgetContent(
                    ref = ref,
                    data = current.data,
                    masked = masked,
                    onOpenPage = openPage,
                )
            }
        }
    }
}

@Composable
private fun HomeWidgetContent(
    ref: HomeWidgetRef,
    data: PluginDataResult,
    masked: Boolean,
    onOpenPage: (String, Map<String, String>) -> Unit,
) {
    when (ref.widget.type) {
        PluginHomeWidgetTypes.SUMMARY -> HomeWidgetSummary(
            data = data,
            masked = masked && ref.widget.sensitive,
        )

        PluginHomeWidgetTypes.PROGRESS -> HomeWidgetProgress(
            data = data,
            masked = masked && ref.widget.sensitive,
        )

        PluginHomeWidgetTypes.LIST -> HomeWidgetList(
            data = data,
            maxItems = ref.widget.maxItems,
            masked = masked && ref.widget.sensitive,
            onOpenPage = onOpenPage,
        )

        PluginHomeWidgetTypes.QUICK_ACTIONS -> HomeWidgetQuickActions(
            ref = ref,
            onOpenPage = onOpenPage,
        )

        else -> Unit
    }
}

@Composable
private fun HomeWidgetSummary(
    data: PluginDataResult,
    masked: Boolean,
) {
    val hero = data.metrics.firstOrNull()
    if (hero == null) {
        HomeWidgetEmpty(data)
        return
    }
    Text(
        text = if (masked) "••••" else hero.value,
        style = MaterialTheme.typography.titleLarge.merge(LifeHubType.tabularNumbers),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = hero.label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val secondary = data.metrics.getOrNull(1)
    if (secondary != null) {
        Text(
            text = "${secondary.label} ${if (masked) "••••" else secondary.value}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeWidgetProgress(
    data: PluginDataResult,
    masked: Boolean,
) {
    val metric = data.metrics.firstOrNull()
    if (metric == null) {
        HomeWidgetEmpty(data)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = metric.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (masked) "••••" else metric.value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    metric.ratio?.let { ratio ->
        val semanticColors = LocalLifeHubSemanticColors.current
        val color = when (metric.tone) {
            PluginDataTone.NEGATIVE -> semanticColors.negative
            PluginDataTone.POSITIVE -> semanticColors.positive
            PluginDataTone.NEUTRAL -> MaterialTheme.colorScheme.primary
        }
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    data.metrics.getOrNull(1)?.let { secondary ->
        Text(
            text = "${secondary.label} ${if (masked) "••••" else secondary.value}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeWidgetList(
    data: PluginDataResult,
    maxItems: Int,
    masked: Boolean,
    onOpenPage: (String, Map<String, String>) -> Unit,
) {
    val items = data.items.take(maxItems.coerceIn(1, 5))
    if (items.isEmpty()) {
        HomeWidgetEmpty(data)
        return
    }
    items.forEachIndexed { index, item ->
        val clickAction = item.clickAction
            ?: item.actions.firstOrNull { it.action is PluginAction.Navigate }?.action
        val rowModifier = if (clickAction is PluginAction.Navigate) {
            Modifier.clickable { onOpenPage(clickAction.pageId, clickAction.params) }
        } else {
            Modifier
        }
        Row(
            modifier = rowModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.trailing.isNotBlank()) {
                Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
                Text(
                    text = if (masked) "••••" else item.trailing,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (index != items.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HomeWidgetQuickActions(
    ref: HomeWidgetRef,
    onOpenPage: (String, Map<String, String>) -> Unit,
) {
    val manifest = ref.descriptor.manifest
    val quickCreate = manifest?.quickCreate
    Row(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
        if (quickCreate != null) {
            Button(
                onClick = { onOpenPage(quickCreate.pageId, emptyMap()) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = LifeHubSpacing.sm),
            ) {
                Text(
                    text = quickCreate.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalButton(
            onClick = {
                onOpenPage(manifest?.entryPage.orEmpty(), emptyMap())
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = LifeHubSpacing.sm),
        ) {
            Text(stringResource(R.string.home_action_open))
        }
    }
}

@Composable
private fun HomeWidgetEmpty(data: PluginDataResult) {
    Text(
        text = data.emptyTitle.ifBlank { stringResource(R.string.home_widget_empty) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (data.emptyMessage.isNotBlank()) {
        Text(
            text = data.emptyMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
