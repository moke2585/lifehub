package com.lifehub.pluginapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPathRulesTest {

    @Test
    fun acceptsNormalRelativePaths() {
        assertTrue(PluginPathRules.isSafeRelativePath("pages/home.json"))
        assertTrue(PluginPathRules.isSafeRelativePath("pages/sub/page.json"))
        assertTrue(PluginPathRules.isSafeRelativePath("manifest.json"))
    }

    @Test
    fun rejectsAbsoluteAndTraversalPaths() {
        assertFalse(PluginPathRules.isSafeRelativePath(""))
        assertFalse(PluginPathRules.isSafeRelativePath("/etc/passwd"))
        assertFalse(PluginPathRules.isSafeRelativePath("../secret.json"))
        assertFalse(PluginPathRules.isSafeRelativePath("pages/../../secret.json"))
        assertFalse(PluginPathRules.isSafeRelativePath("pages/./home.json"))
        assertFalse(PluginPathRules.isSafeRelativePath("pages\\home.json"))
        assertFalse(PluginPathRules.isSafeRelativePath("pages//home.json"))
    }
}
