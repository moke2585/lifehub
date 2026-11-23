package com.lifehub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun parsesLegacyAndScopedEntries() {
        val entries = parseSearchHistory(
            "333\ncom.lifehub.plugin.ledger\t餐饮\n",
        )

        assertEquals(
            listOf(
                SearchHistoryEntry("333"),
                SearchHistoryEntry("餐饮", "com.lifehub.plugin.ledger"),
            ),
            entries,
        )
    }

    @Test
    fun addDedupesByKeywordAndScopeAndKeepsFive() {
        var entries = emptyList<SearchHistoryEntry>()
        entries = addSearchHistory(entries, "餐饮", null)
        entries = addSearchHistory(entries, "餐饮", "com.lifehub.plugin.ledger")
        assertEquals(2, entries.size)

        entries = addSearchHistory(entries, "餐饮", null)
        assertEquals(2, entries.size)
        assertEquals(SearchHistoryEntry("餐饮"), entries.first())

        repeat(6) { index ->
            entries = addSearchHistory(entries, "关键词$index", null)
        }
        assertEquals(MAX_SEARCH_HISTORY, entries.size)
        assertEquals("关键词5", entries.first().keyword)
    }

    @Test
    fun removeOnlyMatchesExactScope() {
        val entries = listOf(
            SearchHistoryEntry("餐饮"),
            SearchHistoryEntry("餐饮", "com.lifehub.plugin.ledger"),
        )

        val remaining = removeSearchHistory(
            entries,
            SearchHistoryEntry("餐饮", "com.lifehub.plugin.ledger"),
        )

        assertEquals(listOf(SearchHistoryEntry("餐饮")), remaining)
    }

    @Test
    fun encodeRoundTripsScopes() {
        val entries = listOf(
            SearchHistoryEntry("咖啡"),
            SearchHistoryEntry("餐饮", "com.lifehub.plugin.ledger"),
        )

        assertEquals(entries, parseSearchHistory(encodeSearchHistory(entries)))
    }

    @Test
    fun rankingPrefersExactThenPrefixThenContains() {
        assertEquals(4, relevanceScore("餐饮", "餐饮"))
        assertEquals(3, relevanceScore("餐饮支出", "餐饮"))
        assertEquals(2, relevanceScore("今日餐饮支出", "餐饮"))
        assertEquals(0, relevanceScore("交通", "餐饮"))
        assertTrue(relevanceScore("餐饮", "餐饮") > relevanceScore("今日餐饮", "餐饮"))
    }
}
