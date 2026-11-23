package com.lifehub.app.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHealthMonitorTest {

    private val store = MemoryHealthStore()
    private var now = 0L
    private val quarantined = mutableListOf<String>()

    private fun monitor(maxFailures: Int = 3): PluginHealthMonitor = PluginHealthMonitor(
        store = store,
        onQuarantine = { pluginId -> quarantined += pluginId },
        maxFailures = maxFailures,
        now = { now },
    )

    @Test
    fun quarantinesAfterThreeFailuresWithinWindow() {
        val monitor = monitor()

        assertEquals(PluginFailureResult.Recorded(1), monitor.recordFailure(PLUGIN_ID))
        now += 1_000
        assertEquals(PluginFailureResult.Recorded(2), monitor.recordFailure(PLUGIN_ID))
        now += 1_000
        assertEquals(PluginFailureResult.Quarantined, monitor.recordFailure(PLUGIN_ID))

        assertEquals(listOf(PLUGIN_ID), quarantined)
        assertTrue(monitor.isQuarantined(PLUGIN_ID))
        assertEquals(setOf(PLUGIN_ID), monitor.quarantinedIds.value)
        assertEquals(3, store.read(PLUGIN_ID)?.failureCount)
    }

    @Test
    fun failuresOutsideWindowStartNewCount() {
        val monitor = monitor()
        val window = 10 * 60 * 1000L

        assertEquals(PluginFailureResult.Recorded(1), monitor.recordFailure(PLUGIN_ID))
        now = window + 1
        assertEquals(PluginFailureResult.Recorded(1), monitor.recordFailure(PLUGIN_ID))
        now += 1_000
        assertEquals(PluginFailureResult.Recorded(2), monitor.recordFailure(PLUGIN_ID))
        assertFalse(monitor.isQuarantined(PLUGIN_ID))
    }

    @Test
    fun successResetsFailureCount() {
        val monitor = monitor()

        monitor.recordFailure(PLUGIN_ID)
        monitor.recordFailure(PLUGIN_ID)
        monitor.recordSuccess(PLUGIN_ID)

        assertNull(store.read(PLUGIN_ID))
        assertEquals(PluginFailureResult.Recorded(1), monitor.recordFailure(PLUGIN_ID))
    }

    @Test
    fun clearRemovesQuarantineAndCount() {
        val monitor = monitor()
        repeat(3) { monitor.recordFailure(PLUGIN_ID) }

        monitor.clear(PLUGIN_ID)

        assertFalse(monitor.isQuarantined(PLUGIN_ID))
        assertNull(store.read(PLUGIN_ID))
        assertEquals(emptySet<String>(), monitor.quarantinedIds.value)
    }

    @Test
    fun directQuarantineMarksPluginImmediately() {
        val monitor = monitor()

        monitor.quarantine(PLUGIN_ID)

        assertTrue(monitor.isQuarantined(PLUGIN_ID))
        assertEquals(true, store.read(PLUGIN_ID)?.quarantined)
        assertEquals(listOf(PLUGIN_ID), quarantined)
    }

    private companion object {
        const val PLUGIN_ID = "com.lifehub.plugin.ledger"
    }
}

private class MemoryHealthStore : PluginHealthStore {
    private val states = mutableMapOf<String, PluginHealthState>()

    override fun read(pluginId: String): PluginHealthState? = states[pluginId]

    override fun write(pluginId: String, state: PluginHealthState) {
        states[pluginId] = state
    }

    override fun clear(pluginId: String) {
        states.remove(pluginId)
    }

    override fun quarantinedIds(): Set<String> =
        states.filterValues { it.quarantined }.keys
}
