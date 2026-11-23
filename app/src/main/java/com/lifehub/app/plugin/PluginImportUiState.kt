package com.lifehub.app.plugin

import com.lifehub.app.plugin.zip.PluginImportPreview

/** 插件 ZIP 导入的 UI 状态。 */
sealed interface PluginImportUiState {
    data object Idle : PluginImportUiState

    data object Inspecting : PluginImportUiState

    data class Preview(val preview: PluginImportPreview) : PluginImportUiState

    data object Importing : PluginImportUiState

    data class Installed(
        val displayName: String,
        val version: String,
    ) : PluginImportUiState

    data class Failed(val message: String) : PluginImportUiState
}
