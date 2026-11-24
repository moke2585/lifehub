package com.lifehub.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.core.content.edit
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.plugin.PluginRegistryState
import com.lifehub.app.plugin.PluginRegistryViewModel
import com.lifehub.app.ui.theme.LifeHubMotion
import com.lifehub.app.ui.theme.LocalReduceMotion
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginStatus

private const val HOME_TAB = "home"
internal const val DEFAULT_SPLIT_RATIO = 0.42f

internal fun clampSplitRatio(value: Float): Float = value.coerceIn(0.28f, 0.65f)

/** 根导航的当前去向，仅用于转场动画；进入比首页更深，返回则更浅。 */
private enum class AppRouteKind { Home, Detail, Page, Search, Backup, Market, MarketDetail }

private data class AppRoute(
    val kind: AppRouteKind,
    val pluginId: String? = null,
    val startPageId: String? = null,
    val startParams: Map<String, String> = emptyMap(),
) {
    val depth: Int
        get() = when (kind) {
            AppRouteKind.Home -> 0
            AppRouteKind.Detail -> 1
            AppRouteKind.Page -> 2
            AppRouteKind.Search -> 1
            AppRouteKind.Backup -> 1
            AppRouteKind.Market -> 1
            AppRouteKind.MarketDetail -> 2
        }
}

/**
 * Material 共享轴 X 转场：进入时新页面从右侧轻推入，返回时反向。
 * 位移只取页面宽度的 1/8–1/24，保持轻快且不会盖过内容本身。
 */
private fun sharedAxisTransition(forward: Boolean, wide: Boolean): ContentTransform {
    val enterSlide: (Int) -> Int = { width ->
        if (forward) width / (if (wide) 8 else 10) else -width / 16
    }
    val exitSlide: (Int) -> Int = { width ->
        if (forward) -width / (if (wide) 16 else 24) else width / (if (wide) 8 else 10)
    }
    return (
        fadeIn(tween(LifeHubMotion.medium, easing = LifeHubMotion.emphasized)) +
            slideInHorizontally(
                animationSpec = tween(LifeHubMotion.medium, easing = LifeHubMotion.emphasized),
                initialOffsetX = enterSlide,
            ) +
            scaleIn(
                animationSpec = tween(LifeHubMotion.medium, easing = LifeHubMotion.emphasized),
                initialScale = 0.985f,
            )
        ) togetherWith (
        fadeOut(tween(LifeHubMotion.short)) +
            slideOutHorizontally(
                animationSpec = tween(LifeHubMotion.medium, easing = LifeHubMotion.standard),
                targetOffsetX = exitSlide,
            ) +
            scaleOut(
                animationSpec = tween(LifeHubMotion.short),
                targetScale = 0.99f,
            )
        )
}

/** 同一层级切换（例如搜索、备份）使用 Material fade-through。 */
private fun fadeThroughTransition(): ContentTransform =
    (
        fadeIn(tween(LifeHubMotion.short, delayMillis = LifeHubMotion.short / 2)) +
            scaleIn(tween(LifeHubMotion.medium), initialScale = 0.97f)
        ) togetherWith (
        fadeOut(tween(LifeHubMotion.short)) +
            scaleOut(tween(LifeHubMotion.short), targetScale = 0.99f)
        )

private fun appRouteTransition(
    from: AppRoute,
    to: AppRoute,
    reduceMotion: Boolean,
): ContentTransform = when {
    reduceMotion -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    to.depth == from.depth -> fadeThroughTransition()
    to.depth > from.depth -> sharedAxisTransition(forward = true, wide = false)
    else -> sharedAxisTransition(forward = false, wide = false)
}

/** 插件内页面栈：只在前进/后退时滑动，同页参数变化只做快速淡入。 */
private fun pageStackTransition(direction: Int, reduceMotion: Boolean): ContentTransform = when {
    reduceMotion -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    direction == 0 -> fadeIn(tween(LifeHubMotion.short)) togetherWith fadeOut(tween(LifeHubMotion.short))
    else -> sharedAxisTransition(forward = direction > 0, wide = true)
}

/**
 * 根导航：底部标签栏固定包含插件管理，其余标签由用户为已启用插件开启。
 */
@Composable
fun LifeHubApp(viewModel: PluginRegistryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val standaloneIds by viewModel.standaloneIds.collectAsStateWithLifecycle()
    val quarantinedIds by viewModel.quarantinedIds.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.inspectPluginZip(uri)
    }
    val launchImport: () -> Unit = { importLauncher.launch(arrayOf("*/*")) }

    val ready = state as? PluginRegistryState.Ready
    if (ready == null) {
        HomeScreen(
            state = state,
            quarantinedIds = quarantinedIds,
            onPluginClick = {},
            onPluginOpen = {},
            onPluginQuickCreate = { _, _ -> },
            onPluginWidgetOpen = { _, _, _ -> },
            loadWidgetData = { _, _, _ ->
                PluginDataResult(
                    emptyTitle = "无法加载",
                    emptyMessage = "插件尚未加载",
                    isError = true,
                )
            },
            onPluginToggle = { _, _ -> },
            onPluginReinstall = {},
            onOpenBackup = {},
            onOpenSearch = {},
            onImportPlugin = launchImport,
            onOpenMarket = {},
        )
    } else {
        PluginShell(
            plugins = ready.plugins,
            standaloneIds = standaloneIds,
            quarantinedIds = quarantinedIds,
            onAction = viewModel::dispatchAction,
            onEnablePlugin = viewModel::enablePlugin,
            onDisablePlugin = viewModel::disablePlugin,
            onReinstallPlugin = viewModel::reinstallPlugin,
            onUninstallPlugin = viewModel::uninstallPlugin,
            onDeletePluginData = viewModel::deletePluginData,
            onLoadData = viewModel::loadPluginData,
            onStandaloneChange = viewModel::setStandalone,
            grantedCapabilities = viewModel::grantedCapabilities,
            onImportPlugin = launchImport,
        )
    }

    PluginImportDialogs(
        state = importState,
        onConfirm = viewModel::confirmPluginImport,
        onCancel = viewModel::cancelPluginImport,
        onDismiss = viewModel::dismissImportState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginShell(
    plugins: List<PluginDescriptor>,
    standaloneIds: Set<String>,
    quarantinedIds: Set<String>,
    onAction: suspend (PluginActionRequest) -> PluginActionResult,
    onEnablePlugin: (String) -> Unit,
    onDisablePlugin: (String) -> Unit,
    onReinstallPlugin: (String) -> Unit,
    onUninstallPlugin: (String) -> Unit,
    onDeletePluginData: suspend (String) -> String,
    onLoadData: suspend (String, String, String, Map<String, String>) -> PluginDataResult,
    onStandaloneChange: (String, Boolean) -> Unit,
    grantedCapabilities: (String) -> Set<String>,
    onImportPlugin: () -> Unit,
) {
    val tabs = remember(plugins, standaloneIds) {
        plugins.filter { it.status == PluginStatus.ENABLED && it.pluginId in standaloneIds }
    }
    var selectedTabId by rememberSaveable { mutableStateOf(HOME_TAB) }
    var homeResetToken by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(tabs) {
        if (selectedTabId != HOME_TAB && tabs.none { it.pluginId == selectedTabId }) {
            selectedTabId = HOME_TAB
        }
    }
    BackHandler(enabled = selectedTabId != HOME_TAB) { selectedTabId = HOME_TAB }

    // 官方 Material 3 自适应导航：手机使用底部导航栏，平板自动切换为左侧 NavigationRail。
    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        navigationSuiteItems = {
            item(
                selected = selectedTabId == HOME_TAB,
                onClick = {
                    selectedTabId = HOME_TAB
                    homeResetToken += 1
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_home),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(R.string.nav_home)) },
            )
            tabs.forEach { plugin ->
                item(
                    selected = selectedTabId == plugin.pluginId,
                    onClick = { selectedTabId = plugin.pluginId },
                    icon = {
                        Icon(
                            painter = painterResource(pluginIconRes(plugin.manifest?.icon)),
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = plugin.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        },
    ) {
        val reduceMotion = LocalReduceMotion.current
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTabId,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        fadeThroughTransition()
                    }
                },
                label = "tab-fade-through",
            ) { tabId ->
                if (tabId == HOME_TAB) {
                    PluginRoutes(
                        plugins = plugins,
                        standaloneIds = standaloneIds,
                        quarantinedIds = quarantinedIds,
                        homeResetToken = homeResetToken,
                        onAction = onAction,
                        onEnablePlugin = onEnablePlugin,
                        onDisablePlugin = onDisablePlugin,
                        onReinstallPlugin = onReinstallPlugin,
                        onUninstallPlugin = onUninstallPlugin,
                        onDeletePluginData = onDeletePluginData,
                        onLoadData = onLoadData,
                        onStandaloneChange = onStandaloneChange,
                        grantedCapabilities = grantedCapabilities,
                        onImportPlugin = onImportPlugin,
                    )
                } else {
                    val plugin = tabs.firstOrNull { it.pluginId == tabId }
                    if (plugin != null) {
                        PluginPageHost(
                            plugin = plugin,
                            startPageId = plugin.manifest?.entryPage.orEmpty(),
                            startParams = emptyMap(),
                            onExit = { selectedTabId = HOME_TAB },
                            onAction = onAction,
                            onLoadData = { pageId, sourceId, params ->
                                onLoadData(plugin.pluginId, pageId, sourceId, params)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRoutes(
    plugins: List<PluginDescriptor>,
    standaloneIds: Set<String>,
    quarantinedIds: Set<String>,
    homeResetToken: Int,
    onAction: suspend (PluginActionRequest) -> PluginActionResult,
    onEnablePlugin: (String) -> Unit,
    onDisablePlugin: (String) -> Unit,
    onReinstallPlugin: (String) -> Unit,
    onUninstallPlugin: (String) -> Unit,
    onDeletePluginData: suspend (String) -> String,
    onLoadData: suspend (String, String, String, Map<String, String>) -> PluginDataResult,
    onStandaloneChange: (String, Boolean) -> Unit,
    grantedCapabilities: (String) -> Set<String>,
    onImportPlugin: () -> Unit,
) {
    var selectedPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var pagePluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var pageStartId by rememberSaveable { mutableStateOf<String?>(null) }
    var pageStartParams by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingEnable by remember { mutableStateOf<PluginDescriptor?>(null) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchScopeId by rememberSaveable { mutableStateOf("") }
    var searchReturn by rememberSaveable { mutableStateOf(false) }
    var marketOpen by rememberSaveable { mutableStateOf(false) }
    var marketPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val searchPrefs = remember {
        context.getSharedPreferences("lifehub_search", Context.MODE_PRIVATE)
    }
    var recentSearches by remember {
        mutableStateOf(parseSearchHistory(searchPrefs.getString("recent", null)))
    }
    val recentPrefs = remember {
        context.getSharedPreferences("lifehub_recent", Context.MODE_PRIVATE)
    }
    var recentPluginIds by remember {
        mutableStateOf(
            recentPrefs.getString("plugins", "").orEmpty()
                .split('\n')
                .filter { it.isNotBlank() },
        )
    }
    val recordRecentPlugin: (String) -> Unit = { pluginId ->
        val updated = (listOf(pluginId) + recentPluginIds.filterNot { it == pluginId })
            .take(5)
        recentPluginIds = updated
        recentPrefs.edit { putString("plugins", updated.joinToString("\n")) }
    }
    val recentPlugins = remember(plugins, recentPluginIds) {
        recentPluginIds.mapNotNull { id ->
            plugins.firstOrNull { it.pluginId == id && it.isOpenable }
        }.take(3)
    }
    val commitSearch: (String, String?) -> Unit = { raw, scopeId ->
        val updated = addSearchHistory(recentSearches, raw, scopeId)
        if (updated != recentSearches) {
            recentSearches = updated
            searchPrefs.edit {
                if (updated.isEmpty()) {
                    remove("recent")
                } else {
                    putString("recent", encodeSearchHistory(updated))
                }
            }
        }
    }
    val removeRecent: (SearchHistoryEntry) -> Unit = { target ->
        val updated = removeSearchHistory(recentSearches, target)
        recentSearches = updated
        searchPrefs.edit {
            if (updated.isEmpty()) {
                remove("recent")
            } else {
                putString("recent", encodeSearchHistory(updated))
            }
        }
    }

    LaunchedEffect(homeResetToken) {
        if (homeResetToken > 0) {
            selectedPluginId = null
            pagePluginId = null
            pageStartId = null
            pageStartParams = emptyMap()
            backupOpen = false
            searchOpen = false
            searchQuery = ""
            searchScopeId = ""
            searchReturn = false
            marketOpen = false
            marketPluginId = null
        }
    }

    val requestToggle: (String, Boolean) -> Unit = { pluginId, enabled ->
        if (!enabled) {
            onDisablePlugin(pluginId)
        } else {
            val descriptor = plugins.firstOrNull { it.pluginId == pluginId }
            if (descriptor != null) {
                if (descriptor.manifest?.capabilities.isNullOrEmpty()) {
                    onEnablePlugin(pluginId)
                } else {
                    pendingEnable = descriptor
                }
            }
        }
    }

    val selectedId = selectedPluginId
    val pageId = pagePluginId
    // 从搜索结果进入插件时，退出插件后回到结果页并保留关键词与范围。
    val exitPage: () -> Unit = {
        pagePluginId = null
        pageStartId = null
        pageStartParams = emptyMap()
        if (searchReturn) {
            searchReturn = false
            searchOpen = true
        }
    }
    val route = when {
        backupOpen -> AppRoute(AppRouteKind.Backup)
        marketPluginId != null -> AppRoute(AppRouteKind.MarketDetail, marketPluginId)
        marketOpen -> AppRoute(AppRouteKind.Market)
        searchOpen -> AppRoute(AppRouteKind.Search)
        pageId != null -> AppRoute(AppRouteKind.Page, pageId, pageStartId, pageStartParams)
        selectedId != null -> AppRoute(AppRouteKind.Detail, selectedId)
        else -> AppRoute(AppRouteKind.Home)
    }
    val reduceMotion = LocalReduceMotion.current

    AnimatedContent(
        targetState = route,
        modifier = Modifier.fillMaxSize(),
        contentKey = { "${it.kind}:${it.pluginId}" },
        transitionSpec = { appRouteTransition(initialState, targetState, reduceMotion) },
        label = "app-route",
    ) { target ->
        when (target.kind) {
            AppRouteKind.Backup -> {
                BackHandler(enabled = backupOpen) { backupOpen = false }
                BackupScreen(onBack = { backupOpen = false })
            }

            AppRouteKind.Market -> MarketplaceRoute(
                initialPluginId = null,
                onOpenDetail = { pluginId -> marketPluginId = pluginId },
                onBack = { marketOpen = false },
            )

            AppRouteKind.MarketDetail -> MarketplaceRoute(
                initialPluginId = target.pluginId,
                onOpenDetail = { pluginId -> marketPluginId = pluginId },
                onBack = { marketPluginId = null },
            )

            AppRouteKind.Search -> {
                GlobalSearchScreen(
                    plugins = plugins,
                    initialQuery = searchQuery,
                    initialScopeId = searchScopeId.ifBlank { null },
                    recentSearches = recentSearches,
                    recentPlugins = recentPlugins,
                    onQueryChange = { searchQuery = it },
                    onScopeChange = { searchScopeId = it.orEmpty() },
                    onCommitSearch = commitSearch,
                    onClearRecent = {
                        recentSearches = emptyList()
                        searchPrefs.edit { remove("recent") }
                    },
                    onRemoveRecent = removeRecent,
                    onBack = {
                        searchOpen = false
                        searchReturn = false
                        // 主动关闭搜索后下次从空白开始，展示最近搜索；从结果返回时仍保留关键词。
                        searchQuery = ""
                        searchScopeId = ""
                    },
                    loadData = { pluginId, sourceId, params ->
                        onLoadData(pluginId, "search", sourceId, params)
                    },
                    onOpenPlugin = { pluginId ->
                        recordRecentPlugin(pluginId)
                        searchOpen = false
                        searchReturn = true
                        pageStartId = null
                        pageStartParams = emptyMap()
                        pagePluginId = pluginId
                    },
                    onOpenPage = { pluginId, pageId, params ->
                        recordRecentPlugin(pluginId)
                        searchOpen = false
                        searchReturn = true
                        pageStartId = pageId
                        pageStartParams = params
                        pagePluginId = pluginId
                    },
                )
            }

            AppRouteKind.Page -> {
                val targetPlugin = target.pluginId?.let { id ->
                    plugins.firstOrNull { it.pluginId == id }
                }
                if (targetPlugin == null || !targetPlugin.isOpenable) {
                    BackHandler(enabled = pageId != null) { exitPage() }
                    ErrorScreen(
                        title = stringResource(R.string.plugin_disabled_title),
                        message = stringResource(R.string.plugin_disabled_message),
                        onBack = exitPage,
                    )
                } else {
                    PluginPageHost(
                        plugin = targetPlugin,
                        startPageId = target.startPageId ?: targetPlugin.manifest?.entryPage.orEmpty(),
                        startParams = target.startParams,
                        onExit = exitPage,
                        onAction = onAction,
                        onLoadData = { currentPageId, sourceId, params ->
                            onLoadData(targetPlugin.pluginId, currentPageId, sourceId, params)
                        },
                    )
                }
            }

            AppRouteKind.Detail -> {
                val targetPlugin = target.pluginId?.let { id ->
                    plugins.firstOrNull { it.pluginId == id }
                }
                BackHandler(enabled = selectedId != null) { selectedPluginId = null }
                if (targetPlugin == null) {
                    ErrorScreen(
                        title = stringResource(R.string.plugin_missing_title),
                        message = stringResource(R.string.plugin_missing_message),
                        onBack = { selectedPluginId = null },
                    )
                } else {
                    PluginDetailScreen(
                        descriptor = targetPlugin,
                        grantedCapabilities = grantedCapabilities(targetPlugin.pluginId),
                        standalone = targetPlugin.pluginId in standaloneIds,
                        quarantined = targetPlugin.pluginId in quarantinedIds,
                        onStandaloneChange = { enabled ->
                            onStandaloneChange(targetPlugin.pluginId, enabled)
                        },
                        onBack = { selectedPluginId = null },
                        onOpenPage = {
                            if (targetPlugin.isOpenable) {
                                pageStartId = null
                                pageStartParams = emptyMap()
                                pagePluginId = targetPlugin.pluginId
                            }
                        },
                        onToggle = { enabled -> requestToggle(targetPlugin.pluginId, enabled) },
                        onReinstall = { onReinstallPlugin(targetPlugin.pluginId) },
                        onUninstall = {
                            onUninstallPlugin(targetPlugin.pluginId)
                            selectedPluginId = null
                        },
                        onDeleteData = { onDeletePluginData(targetPlugin.pluginId) },
                    )
                }
            }

            AppRouteKind.Home -> HomeScreen(
                state = PluginRegistryState.Ready(plugins),
                quarantinedIds = quarantinedIds,
                onPluginClick = { pluginId -> selectedPluginId = pluginId },
                onPluginOpen = { pluginId ->
                    recordRecentPlugin(pluginId)
                    pageStartId = null
                    pageStartParams = emptyMap()
                    pagePluginId = pluginId
                },
                onPluginQuickCreate = { pluginId, pageId ->
                    recordRecentPlugin(pluginId)
                    pageStartId = pageId
                    pageStartParams = emptyMap()
                    pagePluginId = pluginId
                },
                onPluginWidgetOpen = { pluginId, pageId, params ->
                    recordRecentPlugin(pluginId)
                    pageStartId = pageId
                    pageStartParams = params
                    pagePluginId = pluginId
                },
                loadWidgetData = { pluginId, sourceId, params ->
                    onLoadData(pluginId, "home", sourceId, params)
                },
                onPluginToggle = requestToggle,
                onPluginReinstall = onReinstallPlugin,
                onOpenBackup = { backupOpen = true },
                onOpenSearch = { searchOpen = true },
                onImportPlugin = onImportPlugin,
                onOpenMarket = { marketOpen = true },
                recentPluginIds = recentPluginIds,
            )
        }
    }

    pendingEnable?.let { descriptor ->
        CapabilityConsentDialog(
            descriptor = descriptor,
            onConfirm = {
                onEnablePlugin(descriptor.pluginId)
                pendingEnable = null
            },
            onDismiss = { pendingEnable = null },
        )
    }
}

@Composable
private fun PluginPageHost(
    plugin: PluginDescriptor,
    startPageId: String,
    startParams: Map<String, String>,
    onExit: () -> Unit,
    onAction: suspend (PluginActionRequest) -> PluginActionResult,
    onLoadData: suspend (String, String, Map<String, String>) -> PluginDataResult,
) {
    val stack = remember(plugin.pluginId, startPageId, startParams) {
        mutableStateListOf(PageRoute(startPageId, startParams))
    }
    // 返回上一页时恢复滚动位置，避免从详情/编辑回到列表后跳回顶部。
    val scrollStates = remember(plugin.pluginId, startPageId, startParams) {
        mutableMapOf<String, ScrollState>()
    }
    // 缓存每个页面最近一次数据，返回时先渲染旧数据，滚动位置不会被短暂的空内容重置。
    val pageDataCache = remember(plugin.pluginId, startPageId, startParams) {
        mutableMapOf<String, Map<String, PluginDataResult>>()
    }
    val current = stack.last()
    val root = stack.first()
    // 任一页面发生写操作后递增，让仍然挂载的页面（例如分栏左侧的地图）重新取数。
    var dataRevision by remember(plugin.pluginId, startPageId, startParams) {
        mutableIntStateOf(0)
    }
    // 页面栈方向：1 进入、-1 返回、0 同页参数更新；仅用于选择转场动画。
    var navDirection by remember(plugin.pluginId, startPageId, startParams) {
        mutableIntStateOf(0)
    }
    // 分栏收起时暂时保留右侧页面，等淡出动画结束再恢复单栏。
    var closingPane by remember { mutableStateOf<PageRoute?>(null) }
    var paneClosing by remember { mutableStateOf(false) }
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 840
    val actionWithRevision: suspend (PluginActionRequest) -> PluginActionResult = { request ->
        val result = onAction(request)
        if (result is PluginActionResult.Refresh || result is PluginActionResult.Navigate) {
            dataRevision += 1
        }
        result
    }
    val split = wideLayout && (stack.size > 1 || paneClosing)
    val leave: () -> Unit = {
        if (stack.size > 1) {
            navDirection = -1
            val removed = stack.last()
            stack.removeAt(stack.lastIndex)
            if (wideLayout && stack.size == 1) {
                closingPane = removed
                paneClosing = true
            }
        } else {
            onExit()
        }
    }
    BackHandler(onBack = leave)

    if (!plugin.isOpenable) {
        ErrorScreen(
            title = stringResource(R.string.plugin_disabled_title),
            message = stringResource(R.string.plugin_disabled_message),
            onBack = onExit,
        )
        return
    }

    val navigate: (String, Map<String, String>) -> Unit = { targetPageId, params ->
        val beforeSize = stack.size
        val beforeKey = stack.lastOrNull()?.let { "${it.pageId}#${it.params.hashCode()}" }
        navigatePageStack(
            stack = stack,
            targetPageId = targetPageId,
            params = params,
            availablePages = plugin.pages.keys,
        )
        val afterKey = stack.lastOrNull()?.let { "${it.pageId}#${it.params.hashCode()}" }
        navDirection = when {
            stack.size > beforeSize -> 1
            stack.size < beforeSize -> -1
            beforeKey != afterKey -> 0
            else -> navDirection
        }
        if (paneClosing) {
            // 收起过程中又打开了新页面：取消收起，直接显示新页面。
            paneClosing = false
            closingPane = null
        }
    }

    val context = LocalContext.current
    val layoutPrefs = remember {
        context.getSharedPreferences("lifehub_layout", Context.MODE_PRIVATE)
    }
    var splitRatio by remember {
        mutableFloatStateOf(
            clampSplitRatio(layoutPrefs.getFloat("split_ratio", DEFAULT_SPLIT_RATIO)),
        )
    }
    val resizeLabel = stringResource(R.string.layout_split_resize)
    val resetLabel = stringResource(R.string.layout_split_reset)
    val persistSplit: (Float) -> Unit = { value ->
        layoutPrefs.edit { putFloat("split_ratio", value) }
    }
    val reduceMotion = LocalReduceMotion.current
    val pageTransition = pageStackTransition(navDirection, reduceMotion)

    if (split) {
        var splitAppeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { splitAppeared = true }
        val splitEnter by animateFloatAsState(
            targetValue = if (splitAppeared) 1f else 0f,
            animationSpec = tween(
                if (reduceMotion) 0 else LifeHubMotion.medium,
                easing = LifeHubMotion.emphasized,
            ),
            label = "split-enter",
        )
        val splitClose by animateFloatAsState(
            targetValue = if (paneClosing) 0f else 1f,
            animationSpec = tween(
                if (reduceMotion) 0 else LifeHubMotion.medium,
                easing = LifeHubMotion.emphasized,
            ),
            finishedListener = { value ->
                if (value <= 0.01f && paneClosing) {
                    paneClosing = false
                    closingPane = null
                }
            },
            label = "split-close",
        )
        val animatedRatio by animateFloatAsState(
            targetValue = if (paneClosing) 1f else splitRatio,
            animationSpec = tween(
                if (reduceMotion) 0 else LifeHubMotion.medium,
                easing = LifeHubMotion.emphasized,
            ),
            label = "split-close-ratio",
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val handleWidth = 24.dp
            val handleWidthPx = with(LocalDensity.current) { handleWidth.toPx() }
            val dragRange = (constraints.maxWidth.toFloat() - handleWidthPx)
                .coerceAtLeast(1f)
            val currentRatio by rememberUpdatedState(splitRatio)
            val currentRange by rememberUpdatedState(dragRange)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(animatedRatio)) {
                    PluginPagePane(
                        plugin = plugin,
                        route = root,
                        showBackButton = false,
                        onBack = leave,
                        onAction = actionWithRevision,
                        onNavigate = navigate,
                        onLoadData = onLoadData,
                        scrollStates = scrollStates,
                        dataCache = pageDataCache,
                        wideLayout = false,
                        dataRevision = dataRevision,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(handleWidth)
                        .fillMaxHeight()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                splitRatio = clampSplitRatio(
                                    currentRatio + delta / currentRange,
                                )
                            },
                            onDragStopped = { persistSplit(splitRatio) },
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    splitRatio = DEFAULT_SPLIT_RATIO
                                    persistSplit(DEFAULT_SPLIT_RATIO)
                                },
                            )
                        }
                        .semantics {
                            contentDescription = resizeLabel
                            customActions = listOf(
                                CustomAccessibilityAction(resetLabel) {
                                    splitRatio = DEFAULT_SPLIT_RATIO
                                    persistSplit(DEFAULT_SPLIT_RATIO)
                                    true
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .graphicsLayer { alpha = splitClose },
                    ) {
                        VerticalDivider()
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(4.dp)
                                .height(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight((1f - animatedRatio).coerceAtLeast(0.0001f))
                        .graphicsLayer {
                            // 打开时从右侧轻推入；返回时随分栏收起向右淡出。
                            alpha = splitEnter * splitClose
                            translationX = (1f - splitEnter) * size.width / 8f +
                                (1f - splitClose) * size.width / 6f
                        },
                ) {
                    AnimatedContent(
                        targetState = if (stack.size > 1) current else closingPane ?: current,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { pageTransition },
                        contentKey = { "${it.pageId}#${it.params.hashCode()}" },
                        label = "split-page",
                    ) { route ->
                        PluginPagePane(
                            plugin = plugin,
                            route = route,
                            showBackButton = true,
                            onBack = leave,
                            onAction = actionWithRevision,
                            onNavigate = navigate,
                            onLoadData = onLoadData,
                            scrollStates = scrollStates,
                            dataCache = pageDataCache,
                            wideLayout = false,
                            dataRevision = dataRevision,
                        )
                    }
                }
            }
        }
    } else {
        if (plugin.pages[current.pageId] == null) {
            ErrorScreen(
                title = stringResource(R.string.plugin_page_missing_title),
                message = stringResource(R.string.plugin_page_missing_message),
                onBack = leave,
            )
            return
        }
        AnimatedContent(
            targetState = current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageTransition },
            contentKey = { "${it.pageId}#${it.params.hashCode()}" },
            label = "page-stack",
        ) { route ->
            PluginPagePane(
                plugin = plugin,
                route = route,
                showBackButton = true,
                onBack = leave,
                onAction = actionWithRevision,
                onNavigate = navigate,
                onLoadData = onLoadData,
                scrollStates = scrollStates,
                dataCache = pageDataCache,
                wideLayout = null,
                dataRevision = dataRevision,
            )
        }
    }
}

@Composable
private fun PluginPagePane(
    plugin: PluginDescriptor,
    route: PageRoute,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onAction: suspend (PluginActionRequest) -> PluginActionResult,
    onNavigate: (String, Map<String, String>) -> Unit,
    onLoadData: suspend (String, String, Map<String, String>) -> PluginDataResult,
    scrollStates: MutableMap<String, ScrollState>,
    dataCache: MutableMap<String, Map<String, PluginDataResult>>,
    wideLayout: Boolean?,
    dataRevision: Int,
) {
    val document = plugin.pages[route.pageId] ?: return
    val key = "${route.pageId}#${route.params.hashCode()}"
    PluginPageScreen(
        plugin = plugin,
        document = document,
        initialParams = route.params,
        onBack = onBack,
        onAction = onAction,
        onNavigate = onNavigate,
        loadData = { sourceId -> onLoadData(route.pageId, sourceId, route.params) },
        scrollState = scrollStates.getOrPut(key) { ScrollState(0) },
        initialPageData = dataCache[key].orEmpty(),
        onPageDataChange = { data -> dataCache[key] = data },
        wideLayout = wideLayout,
        showBackButton = showBackButton,
        dataRevision = dataRevision,
    )
}

@Composable
private fun CapabilityConsentDialog(
    descriptor: PluginDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.capability_consent_title, descriptor.displayName))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.capability_consent_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                descriptor.manifest?.capabilities.orEmpty().forEach { capability ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = capabilityLabel(capability.id),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = capability.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (capability.reason.isNotBlank()) {
                            Text(
                                text = capability.reason,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.capability_consent_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.capability_consent_cancel))
            }
        },
    )
}
