package com.lifehub.app.ui

import com.lifehub.app.testDescriptor
import com.lifehub.pluginapi.PluginDataItem
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {

    @Test
    fun titleMatchesRankAboveSubtitleMatches() {
        val titleMatch = PluginDataItem(id = "1", title = "咖啡", subtitle = "支出")
        val subtitleMatch = PluginDataItem(id = "2", title = "早餐", subtitle = "咖啡")

        assertTrue(recordRelevance(titleMatch, "咖啡") > recordRelevance(subtitleMatch, "咖啡"))
    }

    @Test
    fun pluginNameRanksAboveDescription() {
        val descriptor = testDescriptor(pluginId = "a")

        assertTrue(pluginRelevance(descriptor, "示例") > pluginRelevance(descriptor, "测试"))
    }
}
