package com.lifehub.app.ui

import com.lifehub.app.testDescriptor
import com.lifehub.pluginapi.PluginStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePluginGroupingTest {

    @Test
    fun groupsPluginsByHomeSectionAndKeepsOrder() {
        val plugins = listOf(
            testDescriptor(pluginId = "enabled", status = PluginStatus.ENABLED),
            testDescriptor(pluginId = "installed", status = PluginStatus.INSTALLED),
            testDescriptor(pluginId = "error", status = PluginStatus.ERROR),
            testDescriptor(pluginId = "disabled", status = PluginStatus.DISABLED),
            testDescriptor(pluginId = "incompatible", status = PluginStatus.INCOMPATIBLE),
            testDescriptor(pluginId = "uninstalled", status = PluginStatus.UNINSTALLED),
        )

        val groups = groupPluginsForHome(plugins)

        assertEquals(listOf("enabled"), groups.enabled.map { it.pluginId })
        assertEquals(listOf("error", "incompatible"), groups.attention.map { it.pluginId })
        assertEquals(
            listOf("installed", "disabled", "uninstalled"),
            groups.available.map { it.pluginId },
        )
    }

    @Test
    fun pinnedPluginsMoveToTopAndKeepRelativeOrder() {
        val plugins = listOf(
            testDescriptor(pluginId = "a"),
            testDescriptor(pluginId = "b"),
            testDescriptor(pluginId = "c"),
            testDescriptor(pluginId = "d"),
        )

        assertEquals(
            listOf("c", "a", "b", "d"),
            sortPinnedPlugins(plugins, setOf("c")).map { it.pluginId },
        )
        assertEquals(
            listOf("b", "d", "a", "c"),
            sortPinnedPlugins(plugins, setOf("b", "d")).map { it.pluginId },
        )
    }

    @Test
    fun recentSortKeepsPinnedFirstThenRecentOrder() {
        val plugins = listOf(
            testDescriptor(pluginId = "a"),
            testDescriptor(pluginId = "b"),
            testDescriptor(pluginId = "c"),
            testDescriptor(pluginId = "d"),
        )

        assertEquals(
            listOf("c", "b", "a", "d"),
            sortHomePlugins(plugins, setOf("c"), listOf("b", "a"), true)
                .map { it.pluginId },
        )
        assertEquals(
            listOf("c", "a", "b", "d"),
            sortHomePlugins(plugins, setOf("c"), listOf("b", "a"), false)
                .map { it.pluginId },
        )
    }
}
