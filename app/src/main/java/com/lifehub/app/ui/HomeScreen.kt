package com.lifehub.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.lifehub.app.BuildConfig
import com.lifehub.app.R
import com.lifehub.app.plugin.PluginRegistryState
import com.lifehub.app.ui.theme.LifeHubContentWidth
import com.lifehub.app.ui.theme.LifeHubElevation
import com.lifehub.app.ui.theme.LifeHubSizes
import com.lifehub.app.ui.theme.LifeHubSpacing
import com.lifehub.app.ui.theme.LifeHubExpressiveShapes
import com.lifehub.app.ui.theme.LifeHubMotion
import com.lifehub.app.ui.theme.LifeHubTheme
import com.lifehub.app.ui.theme.LocalReduceMotion
import com.lifehub.app.ui.theme.LocalThemePreferenceStore
import com.lifehub.app.ui.theme.ThemeMode
import com.lifehub.app.ui.theme.rememberPressScale
import com.lifehub.pluginapi.PluginApiVersion
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginManifest
import com.lifehub.pluginapi.PluginPage
import com.lifehub.pluginapi.PluginStatus

/**
 * 插件主页：已启用的工具优先展示并可直接打开，未启用插件提供明确的启用入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: PluginRegistryState,
    quarantinedIds: Set<String>,
    onPluginClick: (String) -> Unit,
    onPluginOpen: (String) -> Unit,
    onPluginQuickCreate: (String, String) -> Unit,
    onPluginWidgetOpen: (String, String, Map<String, String>) -> Unit,
    loadWidgetData: suspend (String, String, Map<String, String>) -> PluginDataResult,
    onPluginToggle: (String, Boolean) -> Unit,
    onPluginReinstall: (String) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSearch: () -> Unit,
    onImportPlugin: () -> Unit,
    modifier: Modifier = Modifier,
    recentPluginIds: List<String> = emptyList(),
) {
    val themeStore = LocalThemePreferenceStore.current
    var showAppearance by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            LifeHubTopBar(
                title = { LifeHubTopBarTitle(stringResource(R.string.app_name)) },
                actions = {
                    if (themeStore != null) {
                        IconButton(onClick = { showAppearance = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_theme),
                                contentDescription = stringResource(R.string.appearance_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.home_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onImportPlugin) {
                        Icon(
                            painter = painterResource(R.drawable.ic_import),
                            contentDescription = stringResource(R.string.plugin_import_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onOpenBackup,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.home_backup))
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (state) {
            PluginRegistryState.Loading -> LoadingContent(contentModifier)
            is PluginRegistryState.Failed -> FailedContent(state.message, contentModifier)
            is PluginRegistryState.Ready -> PluginHomeContent(
                plugins = state.plugins,
                quarantinedIds = quarantinedIds,
                onPluginClick = onPluginClick,
                onPluginOpen = onPluginOpen,
                onPluginQuickCreate = onPluginQuickCreate,
                onPluginWidgetOpen = onPluginWidgetOpen,
                loadWidgetData = loadWidgetData,
                onPluginToggle = onPluginToggle,
                onPluginReinstall = onPluginReinstall,
                recentPluginIds = recentPluginIds,
                modifier = contentModifier,
            )
        }
    }

    if (showAppearance && themeStore != null) {
        AppearanceDialog(
            current = themeStore.mode,
            dynamicColor = themeStore.dynamicColor,
            dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            reduceMotion = themeStore.reduceMotion,
            onSelect = { mode ->
                themeStore.select(mode)
                showAppearance = false
            },
            onDynamicColorChange = themeStore::selectDynamicColor,
            onReduceMotionChange = themeStore::selectReduceMotion,
            onDismiss = { showAppearance = false },
        )
    }
}

@Composable
private fun AppearanceDialog(
    current: ThemeMode,
    dynamicColor: Boolean,
    dynamicColorSupported: Boolean,
    reduceMotion: Boolean,
    onSelect: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_title)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.appearance_system)
                                ThemeMode.LIGHT -> stringResource(R.string.appearance_light)
                                ThemeMode.DARK -> stringResource(R.string.appearance_dark)
                            },
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = LifeHubSpacing.sm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                if (dynamicColorSupported) {
                    AppearanceToggleRow(
                        title = stringResource(R.string.appearance_dynamic_color),
                        hint = stringResource(R.string.appearance_dynamic_color_hint),
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                }
                AppearanceToggleRow(
                    title = stringResource(R.string.appearance_reduce_motion),
                    hint = stringResource(R.string.appearance_reduce_motion_hint),
                    checked = reduceMotion,
                    onCheckedChange = onReduceMotionChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.appearance_close))
            }
        },
    )
}

@Composable
private fun AppearanceToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(LifeHubSpacing.md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PluginHomeContent(
    plugins: List<PluginDescriptor>,
    quarantinedIds: Set<String>,
    onPluginClick: (String) -> Unit,
    onPluginOpen: (String) -> Unit,
    onPluginQuickCreate: (String, String) -> Unit,
    onPluginWidgetOpen: (String, String, Map<String, String>) -> Unit,
    loadWidgetData: suspend (String, String, Map<String, String>) -> PluginDataResult,
    onPluginToggle: (String, Boolean) -> Unit,
    onPluginReinstall: (String) -> Unit,
    modifier: Modifier = Modifier,
    recentPluginIds: List<String> = emptyList(),
) {
    val groups = remember(plugins) { groupPluginsForHome(plugins) }
    val context = LocalContext.current
    val widgetPrefs = remember(context) {
        context.getSharedPreferences("lifehub_home_widgets", Context.MODE_PRIVATE)
    }
    var widgetConfig by remember {
        mutableStateOf(
            HomeWidgetPreferences(
                order = decodeWidgetKeys(widgetPrefs.getString("order", null)).toList(),
                hidden = decodeWidgetKeys(widgetPrefs.getString("hidden", null)),
            ),
        )
    }
    var editingWidgets by remember { mutableStateOf(false) }
    val availableWidgets = remember(plugins) { availableHomeWidgets(plugins) }
    val orderedWidgets = remember(availableWidgets, widgetConfig) {
        orderHomeWidgets(availableWidgets, widgetConfig)
    }
    val widgets = remember(orderedWidgets, widgetConfig) {
        visibleHomeWidgets(orderedWidgets, widgetConfig)
    }
    val updateWidgetConfig: (HomeWidgetPreferences) -> Unit = { updated ->
        widgetConfig = updated
        widgetPrefs.edit {
            putString("order", updated.order.joinToString("\n"))
            putString("hidden", updated.hidden.joinToString("\n"))
        }
    }
    val pinPrefs = remember(context) {
        context.getSharedPreferences("lifehub_home_plugins", Context.MODE_PRIVATE)
    }
    var pinnedIds by remember {
        mutableStateOf(
            pinPrefs.getString("pinned", "").orEmpty()
                .split('\n').filter { it.isNotBlank() }.toSet(),
        )
    }
    var recentSort by remember {
        mutableStateOf(pinPrefs.getString("recent_sort", "false") == "true")
    }
    val orderedEnabled = remember(groups.enabled, pinnedIds, recentPluginIds, recentSort) {
        sortHomePlugins(groups.enabled, pinnedIds, recentPluginIds, recentSort)
    }
    val togglePin: (String) -> Unit = { pluginId ->
        val updated = if (pluginId in pinnedIds) {
            pinnedIds - pluginId
        } else {
            pinnedIds + pluginId
        }
        pinnedIds = updated
        pinPrefs.edit { putString("pinned", updated.joinToString("\n")) }
    }
    val enabledCount = groups.enabled.size
    val otherCount = plugins.size - enabledCount
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val pageGutter = when {
        windowWidth >= 1200.dp -> LifeHubContentWidth.expanded
        windowWidth >= 840.dp -> LifeHubContentWidth.medium
        else -> LifeHubContentWidth.compact
    }
    val pluginColumns = if (windowWidth >= 1000.dp) 2 else 1
    val enabledColumns = if (orderedEnabled.size == 1) 1 else pluginColumns
    val attentionColumns = if (groups.attention.size == 1) 1 else pluginColumns
    val availableColumns = if (groups.available.size == 1) 1 else pluginColumns

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        MaterialWallpaperBackground()
        LazyColumn(
            modifier = Modifier
                .widthIn(max = LifeHubContentWidth.maxHome)
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                horizontal = pageGutter,
                vertical = LifeHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            item {
                HomeHero(
                    enabledCount = enabledCount,
                    otherCount = otherCount,
                )
            }

            if (availableWidgets.isNotEmpty()) {
                item {
                    HomeWidgetSection(
                        widgets = widgets,
                        loadData = loadWidgetData,
                        onOpen = onPluginWidgetOpen,
                        onEdit = { editingWidgets = true },
                    )
                }
            }

            if (plugins.isEmpty()) {
                item { EmptyPluginsCard() }
            } else {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_section_enabled),
                        count = enabledCount,
                        trailing = {
                            FilterChip(
                                selected = recentSort,
                                onClick = {
                                    recentSort = !recentSort
                                    pinPrefs.edit {
                                        putString("recent_sort", recentSort.toString())
                                    }
                                },
                                modifier = Modifier.height(LifeHubSizes.buttonCompact),
                                label = {
                                    Text(
                                        text = stringResource(
                                            if (recentSort) {
                                                R.string.home_sort_chip_recent
                                            } else {
                                                R.string.home_sort_chip_default
                                            },
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_sort),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        },
                    )
                }
                if (groups.enabled.isEmpty()) {
                    item { EnabledEmptyCard() }
                } else {
                    itemsIndexed(
                        items = orderedEnabled.chunked(enabledColumns),
                        key = { _, chunk -> "enabled-${chunk.first().pluginId}" },
                    ) { index, chunk ->
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .staggeredAppearance(index),
                            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
                        ) {
                            chunk.forEach { descriptor ->
                                PluginHomeCard(
                                    descriptor = descriptor,
                                    quarantined = descriptor.pluginId in quarantinedIds,
                                    pinned = descriptor.pluginId in pinnedIds,
                                    onTogglePin = { togglePin(descriptor.pluginId) },
                                    onDetails = { onPluginClick(descriptor.pluginId) },
                                    onOpen = { onPluginOpen(descriptor.pluginId) },
                                    onQuickCreate = {
                                        descriptor.manifest?.quickCreate?.let { quick ->
                                            onPluginQuickCreate(descriptor.pluginId, quick.pageId)
                                        }
                                    },
                                    onToggle = { enabled ->
                                        onPluginToggle(descriptor.pluginId, enabled)
                                    },
                                    onReinstall = { onPluginReinstall(descriptor.pluginId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(enabledColumns - chunk.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (groups.attention.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.home_section_attention),
                            count = groups.attention.size,
                        )
                    }
                    itemsIndexed(
                        items = groups.attention.chunked(attentionColumns),
                        key = { _, chunk -> "attention-${chunk.first().pluginId}" },
                    ) { index, chunk ->
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .staggeredAppearance(index),
                            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
                        ) {
                            chunk.forEach { descriptor ->
                                PluginHomeCard(
                                    descriptor = descriptor,
                                    quarantined = descriptor.pluginId in quarantinedIds,
                                    onDetails = { onPluginClick(descriptor.pluginId) },
                                    onOpen = { onPluginOpen(descriptor.pluginId) },
                                    onQuickCreate = {
                                        descriptor.manifest?.quickCreate?.let { quick ->
                                            onPluginQuickCreate(descriptor.pluginId, quick.pageId)
                                        }
                                    },
                                    onToggle = { enabled ->
                                        onPluginToggle(descriptor.pluginId, enabled)
                                    },
                                    onReinstall = { onPluginReinstall(descriptor.pluginId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(attentionColumns - chunk.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (groups.available.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.home_section_available),
                            count = groups.available.size,
                        )
                    }
                    itemsIndexed(
                        items = groups.available.chunked(availableColumns),
                        key = { _, chunk -> "available-${chunk.first().pluginId}" },
                    ) { index, chunk ->
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .staggeredAppearance(index),
                            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
                        ) {
                            chunk.forEach { descriptor ->
                                PluginHomeCard(
                                    descriptor = descriptor,
                                    quarantined = descriptor.pluginId in quarantinedIds,
                                    onDetails = { onPluginClick(descriptor.pluginId) },
                                    onOpen = { onPluginOpen(descriptor.pluginId) },
                                    onQuickCreate = {
                                        descriptor.manifest?.quickCreate?.let { quick ->
                                            onPluginQuickCreate(descriptor.pluginId, quick.pageId)
                                        }
                                    },
                                    onToggle = { enabled ->
                                        onPluginToggle(descriptor.pluginId, enabled)
                                    },
                                    onReinstall = { onPluginReinstall(descriptor.pluginId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(availableColumns - chunk.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { VersionFooter() }
        }
    }

    if (editingWidgets) {
        HomeWidgetEditorDialog(
            widgets = orderedWidgets,
            hidden = widgetConfig.hidden,
            visibleCount = widgets.size,
            onMove = { index, delta ->
                val target = index + delta
                if (index in orderedWidgets.indices && target in orderedWidgets.indices) {
                    val reordered = orderedWidgets.toMutableList().apply {
                        add(target, removeAt(index))
                    }
                    updateWidgetConfig(
                        widgetConfig.copy(order = reordered.map { it.key }),
                    )
                }
            },
            onToggle = { key, visible ->
                if (visible && widgets.size >= MAX_VISIBLE_HOME_WIDGETS) {
                    return@HomeWidgetEditorDialog
                }
                updateWidgetConfig(
                    widgetConfig.copy(
                        hidden = if (visible) {
                            widgetConfig.hidden - key
                        } else {
                            widgetConfig.hidden + key
                        },
                    ),
                )
            },
            onRestore = { updateWidgetConfig(HomeWidgetPreferences()) },
            onDismiss = { editingWidgets = false },
        )
    }
}

@Composable
private fun HomeHero(
    enabledCount: Int,
    otherCount: Int,
) {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val floatTransition = rememberInfiniteTransition(label = "hero-float")
    val floatValue by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hero-float-value",
    )
    val subtitle = when {
        enabledCount == 0 -> stringResource(R.string.home_header_subtitle_empty)
        otherCount > 0 -> stringResource(
            R.string.home_header_subtitle_with_more,
            enabledCount,
            otherCount,
        )

        else -> stringResource(R.string.home_header_subtitle_enabled, enabledCount)
    }

    AnimatedVisibility(
        visible = visible || reduceMotion,
        enter = if (reduceMotion) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(LifeHubMotion.long)) +
                slideInVertically(tween(LifeHubMotion.long)) { it / 8 } +
                scaleIn(tween(LifeHubMotion.long), initialScale = 0.96f)
        },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LifeHubExpressiveShapes.hero)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            val showArt = maxWidth >= 520.dp
            Row(
                modifier = Modifier.padding(
                    horizontal = LifeHubSpacing.xl,
                    vertical = LifeHubSpacing.xl,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "LIFEHUB · MATERIAL YOU",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_header_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                    )
                }
                if (showArt) {
                    Box(
                        modifier = Modifier
                            .padding(start = LifeHubSpacing.lg)
                            .size(132.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFBF6E4)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.illustration_tools),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = if (reduceMotion) {
                                        0f
                                    } else {
                                        (floatValue - 0.5f) * 10.dp.toPx()
                                    }
                                    rotationZ = if (reduceMotion) {
                                        0f
                                    } else {
                                        (floatValue - 0.5f) * 2f
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** 工具卡分批入场：按行序错开 40ms，淡入同时轻微上移，减少“整页同时闪现”的生硬感。 */
@Composable
private fun Modifier.staggeredAppearance(index: Int): Modifier {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible || reduceMotion) 1f else 0f,
        animationSpec = tween(
            durationMillis = LifeHubMotion.medium,
            delayMillis = index.coerceIn(0, 6) * 40,
        ),
        label = "stagger-alpha",
    )
    return graphicsLayer {
        this.alpha = alpha
        translationY = 12.dp.toPx() * (1f - alpha)
        val scale = 0.97f + 0.03f * alpha
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LifeHubSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
private fun PluginHomeCard(
    descriptor: PluginDescriptor,
    quarantined: Boolean,
    onDetails: () -> Unit,
    onOpen: () -> Unit,
    onQuickCreate: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onReinstall: () -> Unit,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    onTogglePin: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val manifest = descriptor.manifest
    val description = manifest?.description?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.home_plugin_description_missing)
    val versionLine = manifest?.let {
        stringResource(R.string.plugin_version_publisher, it.version, it.publisher)
    } ?: descriptor.pluginId

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onDetails),
        shape = LifeHubExpressiveShapes.tool,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PluginIconBadge(icon = manifest?.icon)
            Spacer(modifier = Modifier.width(LifeHubSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
            ) {
                Text(
                    text = descriptor.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = versionLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (quarantined) {
                    Text(
                        text = stringResource(R.string.home_quarantined_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = LifeHubSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PluginStatusLabel(descriptor.status)
                    if (pinned) {
                        Spacer(modifier = Modifier.width(LifeHubSpacing.sm))
                        Text(
                            text = stringResource(R.string.home_pinned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    PluginPrimaryAction(
                        descriptor = descriptor,
                        quarantined = quarantined,
                        onOpen = onOpen,
                        onEnable = { onToggle(true) },
                        onReinstall = onReinstall,
                        onDetails = onDetails,
                    )
                    val quickCreate = manifest?.quickCreate
                    if (descriptor.status == PluginStatus.ENABLED && quickCreate != null) {
                        IconButton(
                            onClick = onQuickCreate,
                            modifier = Modifier.size(LifeHubSizes.touchTarget),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = quickCreate.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(LifeHubSizes.iconSmall),
                            )
                        }
                    }
                    if (descriptor.status == PluginStatus.ENABLED) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(LifeHubSizes.touchTarget),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(
                                        R.string.home_more_actions,
                                        descriptor.displayName,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(LifeHubSizes.iconSmall),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (pinned) {
                                                    R.string.home_action_unpin
                                                } else {
                                                    R.string.home_action_pin
                                                },
                                            ),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onTogglePin()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_action_details)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDetails()
                                    },
                                )
                                if (descriptor.canToggle) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_action_disable)) },
                                        onClick = {
                                            menuExpanded = false
                                            onToggle(false)
                                        },
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

@Composable
private fun PluginIconBadge(icon: String?) {
    val (containerColor, contentColor) = when (icon) {
        "ledger" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        "memo" -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer

        "travel" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer

        "hello" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer

        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .background(containerColor, LifeHubExpressiveShapes.badge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(pluginIconRes(icon)),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun PluginStatusLabel(status: PluginStatus) {
    val (containerColor, contentColor) = when (status) {
        PluginStatus.ENABLED -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        PluginStatus.INCOMPATIBLE, PluginStatus.ERROR -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(containerColor, CircleShape)
            .padding(horizontal = LifeHubSpacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = pluginStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun PluginPrimaryAction(
    descriptor: PluginDescriptor,
    quarantined: Boolean,
    onOpen: () -> Unit,
    onEnable: () -> Unit,
    onReinstall: () -> Unit,
    onDetails: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    val buttonModifier = Modifier
        .height(LifeHubSizes.buttonCompact)
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
    when {
        descriptor.isOpenable -> FilledTonalButton(
            onClick = onOpen,
            modifier = buttonModifier,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = LifeHubSpacing.lg),
        ) {
            Text(stringResource(R.string.home_action_open))
        }

        descriptor.status == PluginStatus.UNINSTALLED -> FilledTonalButton(
            onClick = onReinstall,
            modifier = buttonModifier,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = LifeHubSpacing.lg),
        ) {
            Text(stringResource(R.string.home_action_reinstall))
        }

        descriptor.canToggle && descriptor.status != PluginStatus.ENABLED -> Button(
            onClick = onEnable,
            modifier = buttonModifier,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = LifeHubSpacing.lg),
        ) {
            Text(
                stringResource(
                    if (quarantined) {
                        R.string.home_action_reenable
                    } else {
                        R.string.home_action_enable
                    },
                ),
            )
        }

        else -> FilledTonalButton(
            onClick = onDetails,
            modifier = buttonModifier,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = LifeHubSpacing.lg),
        ) {
            Text(stringResource(R.string.home_action_resolve))
        }
    }
}

@Composable
private fun EmptyHomeCard(
    title: String,
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LifeHubExpressiveShapes.tool,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = LifeHubElevation.card),
    ) {
        Row(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(LifeHubExpressiveShapes.badge)
                    .background(Color(0xFFFBF6E4)),
            ) {
                Image(
                    painter = painterResource(R.drawable.illustration_empty),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(modifier = Modifier.width(LifeHubSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EnabledEmptyCard() {
    EmptyHomeCard(
        title = stringResource(R.string.home_empty_enabled_title),
        message = stringResource(R.string.home_empty_enabled_message),
    )
}

@Composable
private fun EmptyPluginsCard() {
    EmptyHomeCard(
        title = stringResource(R.string.home_empty_title),
        message = stringResource(R.string.home_empty_message),
    )
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FailedContent(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(LifeHubSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.home_failed_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VersionFooter()
    }
}

@Composable
private fun VersionFooter() {
    Text(
        text = stringResource(
            R.string.home_version_label,
            BuildConfig.VERSION_NAME,
            PluginApiVersion.current,
        ),
        modifier = Modifier.padding(top = LifeHubSpacing.md),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal data class HomePluginGroups(
    val enabled: List<PluginDescriptor>,
    val attention: List<PluginDescriptor>,
    val available: List<PluginDescriptor>,
)

internal fun groupPluginsForHome(plugins: List<PluginDescriptor>): HomePluginGroups {
    return HomePluginGroups(
        enabled = plugins.filter { it.status == PluginStatus.ENABLED },
        attention = plugins.filter {
            it.status == PluginStatus.INCOMPATIBLE ||
                it.status == PluginStatus.ERROR ||
                it.status == PluginStatus.NOT_INSTALLED
        },
        available = plugins.filter {
            it.status == PluginStatus.INSTALLED ||
                it.status == PluginStatus.DISABLED ||
                it.status == PluginStatus.UNINSTALLED
        },
    )
}

/** 置顶插件排在最前；使用稳定排序保留原有相对顺序。 */
internal fun sortPinnedPlugins(
    plugins: List<PluginDescriptor>,
    pinnedIds: Set<String>,
): List<PluginDescriptor> =
    plugins.sortedWith(compareByDescending { it.pluginId in pinnedIds })

/** 最近使用模式下，置顶仍优先，其余按最近打开顺序排列。 */
internal fun sortHomePlugins(
    plugins: List<PluginDescriptor>,
    pinnedIds: Set<String>,
    recentPluginIds: List<String>,
    recentFirst: Boolean,
): List<PluginDescriptor> {
    if (!recentFirst) return sortPinnedPlugins(plugins, pinnedIds)
    val recentOrder = recentPluginIds.withIndex().associate { it.value to it.index }
    return plugins.sortedWith(
        compareByDescending<PluginDescriptor> { it.pluginId in pinnedIds }
            .thenBy { recentOrder[it.pluginId] ?: Int.MAX_VALUE },
    )
}

private fun previewDescriptor(
    pluginId: String,
    name: String,
    description: String,
    icon: String,
    status: PluginStatus,
): PluginDescriptor {
    val manifest = PluginManifest(
        pluginId = pluginId,
        name = name,
        version = "1.0.0",
        description = description,
        publisher = "LifeHub",
        icon = icon,
        minHostVersion = 1,
        pluginSdkVersion = 1,
        entryPage = "home",
        pages = listOf(PluginPage("home", "示例首页", "pages/home.json")),
    )
    return PluginDescriptor(
        pluginId = pluginId,
        displayName = manifest.name,
        manifest = manifest,
        status = status,
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    LifeHubTheme {
        HomeScreen(
            state = PluginRegistryState.Ready(
                listOf(
                    previewDescriptor(
                        pluginId = "com.lifehub.plugin.ledger",
                        name = "记账",
                        description = "记录每天的收入与支出，按日、月、年查看统计。",
                        icon = "ledger",
                        status = PluginStatus.ENABLED,
                    ),
                    previewDescriptor(
                        pluginId = "com.lifehub.plugin.memo",
                        name = "任务清单",
                        description = "管理任务截止日期与优先级，自动计算紧急程度。",
                        icon = "memo",
                        status = PluginStatus.DISABLED,
                    ),
                    previewDescriptor(
                        pluginId = "com.lifehub.plugin.hello",
                        name = "示例插件",
                        description = "用于演示声明式页面能力的示例插件。",
                        icon = "hello",
                        status = PluginStatus.INCOMPATIBLE,
                    ),
                ),
            ),
            quarantinedIds = emptySet(),
            onPluginClick = {},
            onPluginOpen = {},
            onPluginQuickCreate = { _, _ -> },
            onPluginWidgetOpen = { _, _, _ -> },
            loadWidgetData = { _, _, _ -> PluginDataResult() },
            onPluginToggle = { _, _ -> },
            onPluginReinstall = {},
            onOpenBackup = {},
            onOpenSearch = {},
            onImportPlugin = {},
        )
    }
}
