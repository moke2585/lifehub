package com.lifehub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lifehub.app.R
import com.lifehub.pluginapi.PluginAction
import com.lifehub.pluginapi.PluginDataItem
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MILLIS = 250L
private const val MAX_RESULTS_PER_PLUGIN = 10
private const val MAX_RESULTS_TOTAL = 20

private sealed interface GlobalSearchResult {
    data class PluginItem(val descriptor: PluginDescriptor) : GlobalSearchResult
    data class RecordItem(
        val descriptor: PluginDescriptor,
        val item: PluginDataItem,
    ) : GlobalSearchResult
}

private fun GlobalSearchResult.pluginId(): String = when (this) {
    is GlobalSearchResult.PluginItem -> descriptor.pluginId
    is GlobalSearchResult.RecordItem -> descriptor.pluginId
}

/**
 * 全局 Quick Find：宿主聚合已启用插件的搜索数据源，不包含任何具体插件业务分支。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearchScreen(
    plugins: List<PluginDescriptor>,
    initialQuery: String = "",
    initialScopeId: String? = null,
    recentSearches: List<SearchHistoryEntry> = emptyList(),
    recentPlugins: List<PluginDescriptor> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onScopeChange: (String?) -> Unit = {},
    onCommitSearch: (String, String?) -> Unit = { _, _ -> },
    onClearRecent: () -> Unit = {},
    onRemoveRecent: (SearchHistoryEntry) -> Unit = {},
    onBack: () -> Unit,
    loadData: suspend (String, String, Map<String, String>) -> PluginDataResult,
    onOpenPlugin: (String) -> Unit,
    onOpenPage: (String, String, Map<String, String>) -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var scopeId by rememberSaveable { mutableStateOf(initialScopeId.orEmpty()) }
    var results by remember { mutableStateOf<List<GlobalSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scopePlugins = remember(plugins) {
        plugins.filter { it.status == PluginStatus.ENABLED && it.manifest != null }
    }
    val commitSearch: (String) -> Unit = { keyword ->
        keyword.trim().takeIf { it.isNotEmpty() }?.let {
            onCommitSearch(it, scopeId.ifBlank { null })
        }
    }

    LaunchedEffect(scopePlugins, scopeId) {
        if (scopeId.isNotBlank() && scopePlugins.none { it.pluginId == scopeId }) {
            scopeId = ""
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    LaunchedEffect(query, plugins) {
        val keyword = query.trim()
        results = emptyList()
        searched = false
        if (keyword.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(SEARCH_DEBOUNCE_MILLIS)
        val pluginHits = mutableListOf<GlobalSearchResult.PluginItem>()
        val recordHits = mutableListOf<GlobalSearchResult.RecordItem>()
        val enabled = plugins.filter {
            it.status == PluginStatus.ENABLED && it.manifest != null
        }
        enabled
            .filter { descriptor ->
                descriptor.displayName.contains(keyword, ignoreCase = true) ||
                    descriptor.manifest?.description?.contains(keyword, ignoreCase = true) == true
            }
            .forEach { descriptor ->
                pluginHits += GlobalSearchResult.PluginItem(descriptor)
            }
        enabled.forEach { descriptor ->
            val sourceId = descriptor.manifest?.search?.sourceId ?: return@forEach
            val data = runCatching {
                loadData(
                    descriptor.pluginId,
                    sourceId,
                    mapOf("query" to keyword, "limit" to MAX_RESULTS_PER_PLUGIN.toString()),
                )
            }.getOrNull()
            if (data != null && !data.isError) {
                data.items.take(MAX_RESULTS_PER_PLUGIN).forEach { item ->
                    recordHits += GlobalSearchResult.RecordItem(descriptor, item)
                }
            }
        }
        val ordered = pluginHits.sortedByDescending {
            pluginRelevance(it.descriptor, keyword)
        } + recordHits.sortedByDescending {
            recordRelevance(it.item, keyword)
        }
        if (query.trim() == keyword) {
            results = ordered.take(MAX_RESULTS_TOTAL)
            loading = false
            searched = true
        }
    }

    Scaffold(
        topBar = {
            LifeHubTopBar(
                title = { LifeHubTopBarTitle(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.search_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitSearch(query) }),
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                },
            )

            if (scopePlugins.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = scopeId.isBlank(),
                        onClick = {
                            scopeId = ""
                            onScopeChange(null)
                        },
                        label = { Text(stringResource(R.string.search_scope_all)) },
                    )
                    scopePlugins.forEach { descriptor ->
                        FilterChip(
                            selected = scopeId == descriptor.pluginId,
                            onClick = {
                                scopeId = descriptor.pluginId
                                onScopeChange(descriptor.pluginId)
                            },
                            label = { Text(descriptor.displayName) },
                        )
                    }
                }
            }

            val visibleResults = if (scopeId.isBlank()) {
                results
            } else {
                results.filter { it.pluginId() == scopeId }
            }
            val pluginResults = visibleResults.filterIsInstance<GlobalSearchResult.PluginItem>()
            val recordResults = visibleResults.filterIsInstance<GlobalSearchResult.RecordItem>()

            if (searched && visibleResults.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_count, visibleResults.size),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                query.isBlank() -> Column {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_recent),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onClearRecent) {
                                Text(
                                    text = stringResource(R.string.search_clear_recent),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentSearches.forEach { entry ->
                                val scopeName = scopePlugins
                                    .firstOrNull { it.pluginId == entry.scopeId }
                                    ?.displayName
                                val removeLabel = stringResource(
                                    R.string.search_recent_remove,
                                    entry.keyword,
                                )
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        query = entry.keyword
                                        scopeId = entry.scopeId
                                        onQueryChange(entry.keyword)
                                        onScopeChange(entry.scopeId.ifBlank { null })
                                        onCommitSearch(
                                            entry.keyword,
                                            entry.scopeId.ifBlank { null },
                                        )
                                    },
                                    label = {
                                        Text(
                                            if (scopeName != null) {
                                                "${entry.keyword} · $scopeName"
                                            } else {
                                                entry.keyword
                                            },
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = removeLabel,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onRemoveRecent(entry) },
                                        )
                                    },
                                    modifier = Modifier.semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction(removeLabel) {
                                                onRemoveRecent(entry)
                                                true
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (recentPlugins.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.search_recent_visited),
                            modifier = Modifier.padding(top = 18.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column {
                            recentPlugins.forEach { descriptor ->
                                PluginResultRow(
                                    descriptor = descriptor,
                                    keyword = "",
                                    onClick = { onOpenPlugin(descriptor.pluginId) },
                                )
                            }
                        }
                    }
                    SearchHint()
                }
                loading -> Row(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.search_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                searched && visibleResults.isEmpty() -> Column(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.search_empty_query,
                            query.trim(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.search_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (pluginResults.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_section_plugins),
                                count = pluginResults.size,
                            )
                        }
                        items(pluginResults, key = { "plugin-${it.descriptor.pluginId}" }) { result ->
                            PluginResultRow(
                                descriptor = result.descriptor,
                                keyword = query.trim(),
                                onClick = {
                                    commitSearch(query)
                                    onOpenPlugin(result.descriptor.pluginId)
                                },
                            )
                        }
                    }
                    if (recordResults.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_section_records),
                                count = recordResults.size,
                            )
                        }
                        items(
                            recordResults,
                            key = { result ->
                                "record-${result.descriptor.pluginId}-${result.item.id}"
                            },
                        ) { result ->
                            RecordResultRow(
                                result = result,
                                keyword = query.trim(),
                                onOpenPage = { pluginId, pageId, params ->
                                    commitSearch(query)
                                    onOpenPage(pluginId, pageId, params)
                                },
                                onOpenPlugin = { pluginId ->
                                    commitSearch(query)
                                    onOpenPlugin(pluginId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint() {
    Text(
        text = stringResource(R.string.search_hint),
        modifier = Modifier.padding(top = 20.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchSectionHeader(title: String, count: Int) {
    Text(
        text = "$title · $count",
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PluginResultRow(
    descriptor: PluginDescriptor,
    keyword: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(pluginIconRes(descriptor.manifest?.icon)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightText(descriptor.displayName, keyword),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            descriptor.manifest?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = highlightText(description, keyword),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = stringResource(R.string.search_open_plugin),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RecordResultRow(
    result: GlobalSearchResult.RecordItem,
    keyword: String,
    onOpenPage: (String, String, Map<String, String>) -> Unit,
    onOpenPlugin: (String) -> Unit,
) {
    val item = result.item
    val navigate = item.clickAction as? PluginAction.Navigate
        ?: item.actions.firstOrNull { it.action is PluginAction.Navigate }
            ?.action as? PluginAction.Navigate
    val titleColor = if (item.completed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (navigate != null) {
                    onOpenPage(result.descriptor.pluginId, navigate.pageId, navigate.params)
                } else {
                    onOpenPlugin(result.descriptor.pluginId)
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingText.isNotBlank()) {
            Text(
                text = item.leadingText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(30.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightText(item.title, keyword),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlightText(
                    result.descriptor.displayName +
                        item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    keyword,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.trailing.isNotBlank()) {
            Text(
                text = item.trailing,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 在结果中高亮匹配到的关键词，保留原始大小写。 */
@Composable
private fun highlightText(text: String, keyword: String): AnnotatedString {
    if (keyword.isBlank()) return AnnotatedString(text)
    val style = SpanStyle(
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        background = MaterialTheme.colorScheme.primaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(keyword, startIndex = start, ignoreCase = true)
            if (index < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            withStyle(style) {
                append(text.substring(index, index + keyword.length))
            }
            start = index + keyword.length
        }
    }
}
