package com.lifehub.app.plugin

import com.lifehub.app.capability.CapabilityDecision
import com.lifehub.app.capability.PluginCapabilityBroker
import com.lifehub.app.data.PluginStorageFactory
import com.lifehub.pluginapi.PluginActionContext
import com.lifehub.pluginapi.PluginActionHandler
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult
import com.lifehub.pluginapi.PluginCapabilities
import com.lifehub.pluginapi.PluginSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 宿主侧 action 分发：先经能力代理校验插件状态、manifest 声明与用户授权，
 * 再在后台线程执行处理器，避免 SQLite 等阻塞操作占用主线程。
 */
class PluginActionDispatcher(
    private val handlers: Map<String, PluginActionHandler> = emptyMap(),
    private val capabilities: PluginCapabilityBroker,
    private val storages: PluginStorageFactory,
    private val health: PluginHealthMonitor? = null,
    private val assetReader: ((pluginId: String, path: String) -> String?)? = null,
    private val external: ((PluginActionRequest) -> PluginActionResult?)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun dispatch(request: PluginActionRequest): PluginActionResult =
        withContext(ioDispatcher) {
            when (val decision = capabilities.authorize(request.pluginId, PluginCapabilities.UI_ACTION)) {
                is CapabilityDecision.Denied -> return@withContext PluginActionResult.Failure(decision.reason)
                CapabilityDecision.Granted -> Unit
            }

            val descriptor = capabilities.descriptor(request.pluginId)
            // APK 插件拥有独立进程与数据库，必须走 provider，不能回落到同 ID 的内置 handler。
            if (descriptor?.source == PluginSource.EXTERNAL_APK) {
                val result = external?.invoke(request)
                    ?: PluginActionResult.Failure("插件 APK 未安装：${request.pluginId}")
                health?.recordSuccess(request.pluginId)
                return@withContext result
            }

            val handler = handlers[request.actionId]
            if (handler == null) {
                return@withContext PluginActionResult.Failure("未注册的插件动作：${request.actionId}")
            }

            runCatching {
                val schemaVersion = descriptor
                    ?.manifest
                    ?.dataSchemaVersion
                    ?: 1
                val storage = when (
                    capabilities.authorize(request.pluginId, PluginCapabilities.PRIVATE_STORAGE)
                ) {
                    CapabilityDecision.Granted ->
                        storages.forPlugin(request.pluginId, schemaVersion)

                    is CapabilityDecision.Denied -> null
                }
                handler.handle(
                    PluginActionContext(
                        request = request,
                        storage = storage,
                        assetReader = assetReader?.let { reader ->
                            { path -> reader(request.pluginId, path) }
                        },
                    ),
                )
            }.onSuccess {
                health?.recordSuccess(request.pluginId)
            }.getOrElse { error ->
                health?.recordFailure(request.pluginId)
                PluginActionResult.Failure("插件动作执行失败：${error.message ?: "未知错误"}")
            }
        }
}
