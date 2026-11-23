package com.lifehub.app.capability

import com.lifehub.app.InMemoryGrantStore
import com.lifehub.app.TEST_PLUGIN_ID
import com.lifehub.app.testDescriptor
import com.lifehub.pluginapi.PluginCapabilities
import com.lifehub.pluginapi.PluginStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCapabilityBrokerTest {

    private val grants = InMemoryGrantStore()

    @Test
    fun deniesMissingPlugin() {
        val broker = PluginCapabilityBroker({ null }, grants)

        val decision = broker.authorize(TEST_PLUGIN_ID, PluginCapabilities.UI_ACTION)

        assertTrue(decision is CapabilityDecision.Denied)
    }

    @Test
    fun deniesDisabledPlugin() {
        val descriptor = testDescriptor(status = PluginStatus.DISABLED)
        val broker = PluginCapabilityBroker({ descriptor }, grants)
        grants.grant(TEST_PLUGIN_ID, setOf(PluginCapabilities.UI_ACTION))

        val decision = broker.authorize(TEST_PLUGIN_ID, PluginCapabilities.UI_ACTION)

        assertTrue(decision is CapabilityDecision.Denied)
        assertTrue((decision as CapabilityDecision.Denied).reason.contains("未启用"))
    }

    @Test
    fun deniesUndeclaredCapability() {
        val descriptor = testDescriptor()
        val broker = PluginCapabilityBroker({ descriptor }, grants)
        grants.grant(TEST_PLUGIN_ID, setOf("network.public"))

        val decision = broker.authorize(TEST_PLUGIN_ID, "network.public")

        assertTrue(decision is CapabilityDecision.Denied)
    }

    @Test
    fun deniesCapabilityWithoutUserGrant() {
        val descriptor = testDescriptor()
        val broker = PluginCapabilityBroker({ descriptor }, grants)

        val decision = broker.authorize(TEST_PLUGIN_ID, PluginCapabilities.UI_ACTION)

        assertTrue(decision is CapabilityDecision.Denied)
        assertTrue((decision as CapabilityDecision.Denied).reason.contains("未授权"))
    }

    @Test
    fun grantsDeclaredAndAuthorizedCapability() {
        val descriptor = testDescriptor()
        val broker = PluginCapabilityBroker({ descriptor }, grants)
        grants.grant(TEST_PLUGIN_ID, setOf(PluginCapabilities.UI_ACTION))

        val decision = broker.authorize(TEST_PLUGIN_ID, PluginCapabilities.UI_ACTION)

        assertTrue(decision is CapabilityDecision.Granted)
    }
}
