package com.lifehub.app.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifehub.app.R
import com.lifehub.app.render.PluginPageRenderer
import com.lifehub.app.ui.theme.LifeHubContentWidth
import com.lifehub.app.ui.theme.LifeHubSpacing
import com.lifehub.pluginapi.PluginAction
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult
import com.lifehub.pluginapi.PluginConfirmDialog
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginNode
import com.lifehub.pluginapi.PluginPageDocument
import com.lifehub.pluginapi.PluginTopBarAction
import com.lifehub.pluginapi.PluginTopBarIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPageScreen(
    plugin: PluginDescriptor,
    document: PluginPageDocument,
    initialParams: Map<String, String>,
    onBack: () -> Unit,
    onAction: suspend (PluginActionRequest) -> PluginActionResult,
    onNavigate: (String, Map<String, String>) -> Unit,
    loadData: suspend (String) -> PluginDataResult,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    initialPageData: Map<String, PluginDataResult> = emptyMap(),
    onPageDataChange: (Map<String, PluginDataResult>) -> Unit = {},
    /** 为 null 时按窗口宽度自动判断；分栏宿主里由外部指定，避免在窄栏内再次拆栏。 */
    wideLayout: Boolean? = null,
    showBackButton: Boolean = true,
    /** 宿主写操作后递增的版本号；变化时重新加载本页数据源。 */
    dataRevision: Int = 0,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportContext = LocalContext.current
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    // 连续操作时只保留最新提示，避免每条提示按固定时长排队。
    fun showLatestMessage(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarJob?.cancel()
        snackbarJob = scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = actionLabel != null,
                duration = if (actionLabel != null) {
                    SnackbarDuration.Long
                } else {
                    SnackbarDuration.Short
                },
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }
    val loadFailedTitle = stringResource(R.string.renderer_load_failed)
    val sources = remember(document) { document.dataSources() }
    var refreshToken by remember { mutableIntStateOf(0) }
    var pageData by remember(document.pageId, initialParams) {
        mutableStateOf(initialPageData)
    }
    var slowLoad by remember(document.pageId, initialParams) { mutableStateOf(false) }
    var pendingTopBarAction by remember { mutableStateOf<PluginTopBarAction?>(null) }

    fun runTopBarAction(topAction: PluginTopBarAction) {
        val action = topAction.action
        val params = initialParams + action.extraParams()
        when (action) {
            is PluginAction.Navigate -> onNavigate(action.pageId, params)
            is PluginAction.Host -> scope.launch {
                when (
                    val result = onAction(
                        PluginActionRequest(
                            pluginId = plugin.pluginId,
                            pageId = document.pageId,
                            actionId = action.actionId,
                            params = params,
                        ),
                    )
                ) {
                    is PluginActionResult.Navigate -> onNavigate(result.pageId, result.params)
                    is PluginActionResult.Refresh -> {
                        refreshToken += 1
                        result.message?.let { showLatestMessage(it) }
                    }

                    is PluginActionResult.Failure -> showLatestMessage(result.message)
                    is PluginActionResult.ShowMessage -> showLatestMessage(result.message)
                    is PluginActionResult.ExportImage -> {
                        val saved = saveExportImage(
                            exportContext,
                            result.fileName,
                            result.pngBytes,
                        )
                        showLatestMessage(saved?.let { "已保存到相册：$it" } ?: "导出失败，请稍后重试")
                    }
                    PluginActionResult.Success -> Unit
                }
            }
        }
    }

    LaunchedEffect(document.pageId, initialParams, refreshToken, dataRevision) {
        slowLoad = false
        // 只有加载超过 400ms 才显示骨架，避免一闪而过。
        val slowTimer = launch {
            delay(400)
            slowLoad = true
        }
        val loaded = sources.associateWith { sourceId ->
            runCatching { loadData(sourceId) }.getOrElse { error ->
                PluginDataResult(
                    emptyTitle = loadFailedTitle,
                    emptyMessage = error.message ?: "",
                )
            }
        }
        slowTimer.cancel()
        pageData = loaded
        onPageDataChange(loaded)
    }

    // 顶栏高度固定，标题下的插件名会被裁切，此时只保留页面标题。
    val fontScale = LocalDensity.current.fontScale
    val isWideLayout = wideLayout ?: run {
        val width = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width.toDp()
        }
        width >= 840.dp
    }
    // 单列页面限制 720dp 保证阅读宽度；双列页面需要更宽，避免内部 38/62 分栏被挤窄。
    val contentMaxWidth = if (isWideLayout) {
        LifeHubContentWidth.maxHome
    } else {
        LifeHubContentWidth.maxContent
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LifeHubTopBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (plugin.displayName != document.title && fontScale <= 1.3f) {
                            Text(
                                text = plugin.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.error_back),
                            )
                        }
                    }
                },
                actions = {
                    document.topBarActions.forEach { topAction ->
                        val iconRes = topBarIconRes(topAction.icon) ?: return@forEach
                        IconButton(
                            onClick = {
                                if (topAction.action.confirmDialog() != null) {
                                    pendingTopBarAction = topAction
                                } else {
                                    runTopBarAction(topAction)
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = topAction.contentDescription,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            PluginPageBackdrop(pluginId = plugin.pluginId)
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = LifeHubSpacing.xl,
                        vertical = LifeHubSpacing.lg,
                    ),
            ) {
                if (slowLoad && pageData.isEmpty() && sources.isNotEmpty()) {
                    PluginPageSkeleton()
                } else {
                    key(initialParams) {
                        PluginPageRenderer(
                        pluginId = plugin.pluginId,
                        document = document,
                        initialParams = initialParams,
                        onAction = onAction,
                        onNavigate = onNavigate,
                        onShowMessage = { message, actionLabel, onAction ->
                            showLatestMessage(message, actionLabel, onAction)
                        },
                        pageData = pageData,
                        onRefresh = { refreshToken += 1 },
                        wideLayout = isWideLayout,
                        onExportImage = { fileName, bytes ->
                            saveExportImage(exportContext, fileName, bytes)
                        },
                    )
                }
                }
            }
        }
    }

    val pendingAction = pendingTopBarAction
    val pendingConfirm = pendingAction?.action?.confirmDialog()
    if (pendingAction != null && pendingConfirm != null) {
        AlertDialog(
            onDismissRequest = { pendingTopBarAction = null },
            title = { Text(pendingConfirm.title) },
            text = { Text(pendingConfirm.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTopBarAction = null
                        runTopBarAction(pendingAction)
                    },
                ) {
                    Text(pendingConfirm.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTopBarAction = null }) {
                    Text(pendingConfirm.cancelLabel)
                }
            },
        )
    }
}

@Composable
private fun PluginPageSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.lg)) {
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(LifeHubSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.extraSmall,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.extraSmall,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.extraSmall,
                            ),
                    )
                }
            }
        }
    }
}

private fun topBarIconRes(icon: String): Int? = when (icon) {
    PluginTopBarIcons.ADD -> R.drawable.ic_add
    PluginTopBarIcons.TRASH -> R.drawable.ic_trash
    PluginTopBarIcons.CHART -> R.drawable.ic_chart
    PluginTopBarIcons.NOTES -> R.drawable.ic_notes
    PluginTopBarIcons.SEARCH -> R.drawable.ic_search
    PluginTopBarIcons.LOANS -> R.drawable.ic_loans
    else -> null
}

/** 把插件生成的 PNG 写入系统相册；旧系统回退到应用专属图片目录。 */
private suspend fun saveExportImage(context: Context, fileName: String, bytes: ByteArray): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LifeHub")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "相册/Pictures/LifeHub/$fileName"
            } else {
                val directory = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "LifeHub",
                ).apply { mkdirs() }
                val file = File(directory, fileName)
                file.writeBytes(bytes)
                file.absolutePath
            }
        }.getOrNull()
    }

/** 每个插件一套浅/深背景；顶部轻、底部重的 scrim 保证列表与图表可读。 */
@Composable
private fun PluginPageBackdrop(pluginId: String) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val artRes = when {
        pluginId.endsWith(".ledger") ->
            if (dark) R.drawable.plugin_bg_ledger_dark else R.drawable.plugin_bg_ledger_light

        pluginId.endsWith(".memo") ->
            if (dark) R.drawable.plugin_bg_memo_dark else R.drawable.plugin_bg_memo_light

        pluginId.endsWith(".travel") ->
            if (dark) R.drawable.plugin_bg_travel_dark else R.drawable.plugin_bg_travel_light

        else -> null
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (artRes != null) {
            Image(
                painter = painterResource(artRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (dark) 0.72f else 0.82f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.surface.copy(alpha = if (dark) 0.22f else 0.18f),
                        0.42f to scheme.surface.copy(alpha = if (dark) 0.62f else 0.52f),
                        1f to scheme.surface.copy(alpha = if (dark) 0.96f else 0.94f),
                    ),
                ),
        )
    }
}

private fun PluginAction.confirmDialog(): PluginConfirmDialog? = when (this) {
    is PluginAction.Host -> confirm
    is PluginAction.Navigate -> confirm
}

private fun PluginAction.extraParams(): Map<String, String> = when (this) {
    is PluginAction.Host -> params
    is PluginAction.Navigate -> params
}

private fun PluginPageDocument.dataSources(): List<String> = buildList {
    val seen = mutableSetOf<String>()

    fun visit(nodes: List<PluginNode>) {
        nodes.forEach { node ->
            when (node) {
                is PluginNode.RecordList -> if (seen.add(node.sourceId)) add(node.sourceId)
                is PluginNode.Metrics -> if (seen.add(node.sourceId)) add(node.sourceId)
                is PluginNode.BarChart -> if (seen.add(node.sourceId)) add(node.sourceId)
                is PluginNode.DonutChart -> if (seen.add(node.sourceId)) add(node.sourceId)
                is PluginNode.GeoMap -> if (seen.add(node.sourceId)) add(node.sourceId)
                is PluginNode.PeriodNav -> if (seen.add(node.sourceId)) add(node.sourceId)
                is PluginNode.Section -> visit(node.children)
                is PluginNode.Row -> visit(node.children)
                else -> Unit
            }
        }
    }
    visit(nodes)
}
