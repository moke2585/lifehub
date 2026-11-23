package com.lifehub.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lifehub.app.R
import com.lifehub.pluginapi.PluginCapabilities
import com.lifehub.pluginapi.PluginStatus

@Composable
internal fun pluginStatusLabel(status: PluginStatus): String = stringResource(
    when (status) {
        PluginStatus.INSTALLED -> R.string.plugin_status_installed
        PluginStatus.ENABLED -> R.string.plugin_status_enabled
        PluginStatus.DISABLED -> R.string.plugin_status_disabled
        PluginStatus.UNINSTALLED -> R.string.plugin_status_uninstalled
        PluginStatus.INCOMPATIBLE -> R.string.plugin_status_incompatible
        PluginStatus.ERROR -> R.string.plugin_status_error
    },
)

@Composable
internal fun pluginStatusColor(status: PluginStatus): Color = when (status) {
    PluginStatus.ENABLED -> MaterialTheme.colorScheme.primary
    PluginStatus.INCOMPATIBLE, PluginStatus.ERROR -> MaterialTheme.colorScheme.error
    PluginStatus.INSTALLED, PluginStatus.DISABLED, PluginStatus.UNINSTALLED ->
        MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun capabilityLabel(capabilityId: String): String = when (capabilityId) {
    PluginCapabilities.UI_ACTION -> stringResource(R.string.capability_ui_action)
    PluginCapabilities.PRIVATE_STORAGE -> stringResource(R.string.capability_storage_private)
    else -> capabilityId
}
