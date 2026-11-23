package com.lifehub.app.plugin

import com.lifehub.app.FakePluginStorage
import com.lifehub.app.InMemoryGrantStore
import com.lifehub.app.TEST_PLUGIN_ID
import com.lifehub.app.capability.PluginCapabilityBroker
import com.lifehub.app.data.PluginStorageFactory
import com.lifehub.app.testDescriptor
import com.lifehub.pluginapi.PluginActionHandler
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult
import com.lifehub.pluginapi.PluginCapabilities
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginStatus
import com.lifehub.pluginapi.PluginStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginActionDispatcherTest {

    private val actionId = BuiltInPluginActions.HELLO_GREET
    private val grants = InMemoryGrantStore()
    private val storage = FakePluginStorage()

    @Test
    fun unknownActionReturnsFailure() = runBlocking {
        grants.grant(TEST_PLUGIN_ID, setOf(PluginCapabilities.UI_ACTION))

        val result = dispatcher(emptyMap()).dispatch(request())

        assertTrue(result is PluginActionResult.Failure)
        assertTrue((result as PluginActionResult.Failure).message.contains(actionId))
    }

    @Test
    fun missingUserGrantIsDenied() = runBlocking {
        val result = dispatcher(
            mapOf(actionId to PluginActionHandler { PluginActionResult.Success }),
        ).dispatch(request())

        assertTrue(result is PluginActionResult.Failure)
        assertTrue((result as PluginActionResult.Failure).message.contains("未授权"))
    }

    @Test
    fun disabledPluginIsDenied() = runBlocking {
        grants.grant(TEST_PLUGIN_ID, setOf(PluginCapabilities.UI_ACTION))
        val result = dispatcher(
            handlers = mapOf(actionId to PluginActionHandler { PluginActionResult.Success }),
            descriptor = testDescriptor(status = PluginStatus.DISABLED),
        ).dispatch(request())

        assertTrue(result is PluginActionResult.Failure)
        assertTrue((result as PluginActionResult.Failure).message.contains("未启用"))
    }

    @Test
    fun handlerReceivesStorageWhenGranted() = runBlocking {
        grants.grant(
            TEST_PLUGIN_ID,
            setOf(PluginCapabilities.UI_ACTION, PluginCapabilities.PRIVATE_STORAGE),
        )
        var storageSeen: PluginStorage? = null

        val result = dispatcher(
            mapOf(
                actionId to PluginActionHandler { context ->
                    storageSeen = context.storage
                    PluginActionResult.ShowMessage("ok")
                },
            ),
        ).dispatch(request())

        assertEquals(PluginActionResult.ShowMessage("ok"), result)
        assertNotNull(storageSeen)
    }

    @Test
    fun storageIsNullWithoutStorageGrant() = runBlocking {
        grants.grant(TEST_PLUGIN_ID, setOf(PluginCapabilities.UI_ACTION))
        var storageSeen: PluginStorage? = null

        dispatcher(
            mapOf(
                actionId to PluginActionHandler { context ->
                    storageSeen = context.storage
                    PluginActionResult.Success
                },
            ),
        ).dispatch(request())

        assertNull(storageSeen)
    }

    @Test
    fun constructingDispatcherDoesNotInvokeHandlers() {
        var invoked = false

        dispatcher(
            handlers = mapOf(
                actionId to PluginActionHandler {
                    invoked = true
                    PluginActionResult.Success
                },
            ),
        )

        assertFalse(invoked)
    }

    @Test
    fun unexpectedHandlerExceptionIsCountedByHealthMonitor() = runBlocking {
        grants.grant(TEST_PLUGIN_ID, setOf(PluginCapabilities.UI_ACTION))
        val healthStore = TestHealthStore()
        val health = PluginHealthMonitor(store = healthStore)

        val result = dispatcher(
            handlers = mapOf(
                actionId to PluginActionHandler { error("boom") },
            ),
            health = health,
        ).dispatch(request())

        assertTrue(result is PluginActionResult.Failure)
        assertEquals(1, healthStore.read(TEST_PLUGIN_ID)?.failureCount)
    }

    @Test
    fun builtInHelloActionUsesIsolatedStorage() = runBlocking {
        grants.grant(
            TEST_PLUGIN_ID,
            setOf(PluginCapabilities.UI_ACTION, PluginCapabilities.PRIVATE_STORAGE),
        )
        val dispatcher = dispatcher(BuiltInPluginActions.handlers)

        val first = dispatcher.dispatch(request())
        val second = dispatcher.dispatch(request())

        assertEquals(PluginActionResult.ShowMessage("你好，小明（第 1 次问候）"), first)
        assertEquals(PluginActionResult.ShowMessage("你好，小明（第 2 次问候）"), second)
        assertEquals(1, storage.count("meta"))
    }

    private fun dispatcher(
        handlers: Map<String, PluginActionHandler>,
        descriptor: PluginDescriptor = testDescriptor(),
        health: PluginHealthMonitor? = null,
    ): PluginActionDispatcher = PluginActionDispatcher(
        handlers = handlers,
        capabilities = PluginCapabilityBroker(
            descriptorProvider = { pluginId -> descriptor.takeIf { it.pluginId == pluginId } },
            grants = grants,
        ),
        storages = PluginStorageFactory { _, _ -> storage },
        health = health,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun request(params: Map<String, String> = mapOf("name" to "小明")) =
        PluginActionRequest(
            pluginId = TEST_PLUGIN_ID,
            pageId = "home",
            actionId = actionId,
            params = params,
        )
}

private class TestHealthStore : PluginHealthStore {
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
