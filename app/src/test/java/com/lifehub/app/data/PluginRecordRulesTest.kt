package com.lifehub.app.data

import com.lifehub.pluginapi.PluginStorageLimits
import org.junit.Test

class PluginRecordRulesTest {

    @Test
    fun acceptsValidNamesAndPage() {
        PluginRecordRules.requireCollection("greeting_meta")
        PluginRecordRules.requireId("greeting_count")
        PluginRecordRules.requirePayload("""{"value":1}""")
        PluginRecordRules.requirePage(PluginStorageLimits.MAX_PAGE_SIZE, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCollectionWithSlash() {
        PluginRecordRules.requireCollection("../secret")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUppercaseCollection() {
        PluginRecordRules.requireCollection("Meta")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlongPayload() {
        PluginRecordRules.requirePayload("a".repeat(PluginStorageLimits.MAX_RECORD_BYTES + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRecordId() {
        PluginRecordRules.requireId("bad id")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroLimit() {
        PluginRecordRules.requirePage(limit = 0, offset = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeOffset() {
        PluginRecordRules.requirePage(limit = 10, offset = -1)
    }
}
