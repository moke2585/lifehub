package com.lifehub.app.capability

import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginStatus

sealed interface CapabilityDecision {
    data object Granted : CapabilityDecision

    data class Denied(val reason: String) : CapabilityDecision
}

/**
 * 能力代理：每次调用都同时检查插件状态、manifest 声明和用户授权。
 * 不因为 manifest 声明了某个能力就自动放行。
 */
class PluginCapabilityBroker(
    private val descriptorProvider: (String) -> PluginDescriptor?,
    private val grants: PluginCapabilityGrantStore,
) {
    fun descriptor(pluginId: String): PluginDescriptor? = descriptorProvider(pluginId)

    fun authorize(pluginId: String, capabilityId: String): CapabilityDecision {
        val descriptor = descriptorProvider(pluginId)
            ?: return CapabilityDecision.Denied("插件不存在：$pluginId")
        if (descriptor.status != PluginStatus.ENABLED) {
            return CapabilityDecision.Denied("插件未启用：$pluginId")
        }
        val manifest = descriptor.manifest
            ?: return CapabilityDecision.Denied("插件清单不可用：$pluginId")
        if (manifest.capabilities.none { it.id == capabilityId }) {
            return CapabilityDecision.Denied("插件未声明能力：$capabilityId")
        }
        if (!grants.isGranted(pluginId, capabilityId)) {
            return CapabilityDecision.Denied("用户未授权能力：$capabilityId")
        }
        return CapabilityDecision.Granted
    }
}
