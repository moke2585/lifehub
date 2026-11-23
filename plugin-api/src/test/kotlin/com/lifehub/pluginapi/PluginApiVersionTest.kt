package com.lifehub.pluginapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiVersionTest {

    @Test
    fun currentVersionMatchesConstants() {
        assertEquals("1.0.0", PluginApiVersion.current)
    }

    @Test
    fun onlySameMajorVersionIsCompatible() {
        assertTrue(PluginApiVersion.isCompatible(PluginApiVersion.MAJOR))
        assertFalse(PluginApiVersion.isCompatible(PluginApiVersion.MAJOR + 1))
        assertFalse(PluginApiVersion.isCompatible(PluginApiVersion.MAJOR - 1))
    }
}
