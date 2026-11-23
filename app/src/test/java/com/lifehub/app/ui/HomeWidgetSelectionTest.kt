package com.lifehub.app.ui

import com.lifehub.app.testDescriptor
import com.lifehub.pluginapi.PluginHomeWidget
import com.lifehub.pluginapi.PluginHomeWidgetTypes
import com.lifehub.pluginapi.PluginStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWidgetSelectionTest {

    @Test
    fun selectsEnabledWidgetsByPriorityAndCapsAtThree() {
        val plugins = listOf(
            testDescriptor(
                pluginId = "a",
                homeWidgets = listOf(
                    widget("a-low", priority = 10),
                    widget("a-high", priority = 50),
                ),
            ),
            testDescriptor(
                pluginId = "b",
                homeWidgets = listOf(widget("b-top", priority = 100)),
            ),
            testDescriptor(
                pluginId = "c",
                homeWidgets = listOf(
                    widget("c-1", priority = 1),
                    widget("c-2", priority = 20),
                    widget("c-3", priority = 30),
                    widget("c-4", priority = 40),
                ),
            ),
        )

        val selected = buildHomeWidgets(plugins)

        assertEquals(listOf("b-top", "a-high", "c-4"), selected.map { it.widget.id })
    }

    @Test
    fun ignoresUnknownTypesAndDataWidgetsWithoutSource() {
        val plugins = listOf(
            testDescriptor(
                pluginId = "a",
                homeWidgets = listOf(
                    widget("future", type = "heatmap", sourceId = null),
                    widget("broken", type = PluginHomeWidgetTypes.SUMMARY, sourceId = null),
                    widget("quick", type = PluginHomeWidgetTypes.QUICK_ACTIONS, sourceId = null),
                ),
            ),
        )

        val selected = buildHomeWidgets(plugins)

        assertEquals(listOf("quick"), selected.map { it.widget.id })
    }

    @Test
    fun ignoresDisabledPlugins() {
        val plugins = listOf(
            testDescriptor(
                pluginId = "disabled",
                status = PluginStatus.DISABLED,
                homeWidgets = listOf(widget("hidden", priority = 100)),
            ),
            testDescriptor(
                pluginId = "enabled",
                homeWidgets = listOf(widget("visible", priority = 10)),
            ),
        )

        val selected = buildHomeWidgets(plugins)

        assertEquals(listOf("visible"), selected.map { it.widget.id })
    }

    @Test
    fun availableKeepsAllWidgetsWhileDefaultSelectionCapsAtThree() {
        val plugins = listOf(
            testDescriptor(
                pluginId = "a",
                homeWidgets = listOf(
                    widget("w1", priority = 10),
                    widget("w2", priority = 20),
                    widget("w3", priority = 30),
                    widget("w4", priority = 40),
                ),
            ),
        )

        assertEquals(
            listOf("w4", "w3", "w2", "w1"),
            availableHomeWidgets(plugins).map { it.widget.id },
        )
        assertEquals(3, buildHomeWidgets(plugins).size)
    }

    @Test
    fun customOrderAndHiddenPreferencesAreApplied() {
        val plugins = listOf(
            testDescriptor(
                pluginId = "a",
                homeWidgets = listOf(
                    widget("w1", priority = 10),
                    widget("w2", priority = 20),
                    widget("w3", priority = 30),
                    widget("w4", priority = 40),
                ),
            ),
        )
        val available = availableHomeWidgets(plugins)
        val preferences = HomeWidgetPreferences(
            order = listOf("a/w4", "a/w1", "a/w2", "a/w3"),
            hidden = setOf("a/w2"),
        )

        val ordered = orderHomeWidgets(available, preferences)
        assertEquals(
            listOf("w4", "w1", "w2", "w3"),
            ordered.map { it.widget.id },
        )
        assertEquals(
            listOf("w4", "w1", "w3"),
            visibleHomeWidgets(ordered, preferences).map { it.widget.id },
        )
        assertEquals(
            listOf("w4", "w1", "w2"),
            visibleHomeWidgets(ordered, preferences.copy(hidden = emptySet()))
                .map { it.widget.id },
        )
    }

    @Test
    fun unknownKeysAreIgnoredAndDefaultOrderIsRestored() {
        val plugins = listOf(
            testDescriptor(
                pluginId = "a",
                homeWidgets = listOf(
                    widget("w1", priority = 10),
                    widget("w2", priority = 20),
                ),
            ),
        )
        val available = availableHomeWidgets(plugins)

        val ordered = orderHomeWidgets(
            available,
            HomeWidgetPreferences(order = listOf("gone/widget", "a/w2")),
        )
        assertEquals(listOf("w2", "w1"), ordered.map { it.widget.id })

        assertEquals(
            listOf("w2", "w1"),
            orderHomeWidgets(available, HomeWidgetPreferences()).map { it.widget.id },
        )
        assertEquals(setOf("a/b", "c/d"), decodeWidgetKeys("a/b\n\n c/d \n"))
    }

    private fun widget(
        id: String,
        type: String = PluginHomeWidgetTypes.SUMMARY,
        priority: Int = 0,
        sourceId: String? = "source",
    ): PluginHomeWidget = PluginHomeWidget(
        id = id,
        type = type,
        title = id,
        priority = priority,
        sourceId = sourceId,
    )
}
