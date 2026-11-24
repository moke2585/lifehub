package com.lifehub.app.plugin.runtime.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginVersionTest {

    @Test
    fun comparesNumericSegments() {
        assertTrue(PluginVersion.compare("1.2.0", "1.1.9") > 0)
        assertTrue(PluginVersion.compare("1.10.0", "1.9.0") > 0)
        assertEquals(0, PluginVersion.compare("3.0.0", "3.0.0"))
        assertTrue(PluginVersion.compare("0.9.0", "1.0.0") < 0)
    }

    @Test
    fun ignoresPreReleaseAndBuildSuffixes() {
        assertEquals(0, PluginVersion.compare("1.2.0-beta.1", "1.2.0"))
        assertEquals(0, PluginVersion.compare("1.2.0+42", "1.2.0"))
    }

    @Test
    fun treatsMissingSegmentsAsZero() {
        assertEquals(0, PluginVersion.compare("1.2", "1.2.0"))
        assertTrue(PluginVersion.compare("1.2.1", "1.2") > 0)
    }

    @Test
    fun isNewerMatchesCompare() {
        assertTrue(PluginVersion.isNewer("1.2.0", "1.1.9"))
        assertFalse(PluginVersion.isNewer("1.1.0", "1.1.0"))
    }
}
