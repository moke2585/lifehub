package com.lifehub.app.ui

/** 一条最近搜索：关键词与当时的插件范围（空串表示全部）。 */
internal data class SearchHistoryEntry(
    val keyword: String,
    val scopeId: String = "",
)

internal const val MAX_SEARCH_HISTORY = 5

internal fun parseSearchHistory(raw: String?): List<SearchHistoryEntry> =
    raw.orEmpty()
        .split('\n')
        .filter { it.isNotBlank() }
        .map { line ->
            val separator = line.indexOf('\t')
            if (separator >= 0) {
                SearchHistoryEntry(
                    keyword = line.substring(separator + 1).trim(),
                    scopeId = line.substring(0, separator).trim(),
                )
            } else {
                // 旧版本只保存关键词。
                SearchHistoryEntry(keyword = line.trim())
            }
        }
        .filter { it.keyword.isNotBlank() }

internal fun encodeSearchHistory(entries: List<SearchHistoryEntry>): String =
    entries.joinToString("\n") { "${it.scopeId}\t${it.keyword}" }

internal fun addSearchHistory(
    entries: List<SearchHistoryEntry>,
    keyword: String,
    scopeId: String?,
): List<SearchHistoryEntry> {
    val normalized = keyword.trim()
    if (normalized.isBlank()) return entries
    val scope = scopeId.orEmpty().trim()
    return (
        listOf(SearchHistoryEntry(normalized, scope)) +
            entries.filterNot { it.keyword == normalized && it.scopeId == scope }
        ).take(MAX_SEARCH_HISTORY)
}

internal fun removeSearchHistory(
    entries: List<SearchHistoryEntry>,
    target: SearchHistoryEntry,
): List<SearchHistoryEntry> = entries.filterNot {
    it.keyword == target.keyword && it.scopeId == target.scopeId
}
