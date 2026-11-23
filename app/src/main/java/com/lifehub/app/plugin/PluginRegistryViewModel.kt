package com.lifehub.app.plugin

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifehub.app.LifeHubApplication
import com.lifehub.app.plugin.zip.PluginImportResult
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult as ActionExecutionResult
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginPageDataRequest
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginRegistryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LifeHubApplication
    private val registry = app.pluginRegistry
    private val grants = app.capabilityGrants
    private val tabPreferences = app.pluginTabPreferences
    private val health = app.pluginHealthMonitor
    private val importer = app.pluginZipImporter

    val state: StateFlow<PluginRegistryState> = registry.state
    val quarantinedIds: StateFlow<Set<String>> = health.quarantinedIds

    private val _standaloneIds = MutableStateFlow(tabPreferences.standalonePluginIds())
    val standaloneIds: StateFlow<Set<String>> = _standaloneIds.asStateFlow()

    private val _importState = MutableStateFlow<PluginImportUiState>(PluginImportUiState.Idle)
    val importState: StateFlow<PluginImportUiState> = _importState.asStateFlow()

    init {
        viewModelScope.launch { registry.load() }
    }

    fun inspectPluginZip(uri: Uri) {
        (_importState.value as? PluginImportUiState.Preview)?.let { importer.discard(it.preview) }
        viewModelScope.launch {
            _importState.value = PluginImportUiState.Inspecting
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { importer.inspect(it) }
                        ?: PluginImportResult.Failure("无法读取所选文件")
                }.getOrElse { error ->
                    PluginImportResult.Failure(
                        "读取插件包失败：${error.message ?: "未知错误"}",
                    )
                }
            }
            _importState.value = when (result) {
                is PluginImportResult.Ready -> PluginImportUiState.Preview(result.preview)
                is PluginImportResult.Failure -> PluginImportUiState.Failed(result.message)
                is PluginImportResult.Installed -> PluginImportUiState.Failed("插件包状态异常")
            }
        }
    }

    fun confirmPluginImport() {
        val preview = (_importState.value as? PluginImportUiState.Preview)?.preview ?: return
        viewModelScope.launch {
            _importState.value = PluginImportUiState.Importing
            when (val result = withContext(Dispatchers.IO) { importer.commit(preview) }) {
                is PluginImportResult.Installed -> {
                    registry.reload()
                    // 重新导入已卸载的插件等同于安装，恢复为“未启用”而不是继续留在卸载状态。
                    if (registry.plugin(result.pluginId)?.status == PluginStatus.UNINSTALLED) {
                        registry.reinstall(result.pluginId)
                    }
                    _importState.value = PluginImportUiState.Installed(
                        displayName = result.displayName,
                        version = result.version,
                    )
                }

                is PluginImportResult.Failure -> {
                    _importState.value = PluginImportUiState.Failed(result.message)
                }

                is PluginImportResult.Ready -> {
                    _importState.value = PluginImportUiState.Failed("插件包状态异常")
                }
            }
        }
    }

    fun cancelPluginImport() {
        (_importState.value as? PluginImportUiState.Preview)?.let { importer.discard(it.preview) }
        _importState.value = PluginImportUiState.Idle
    }

    fun dismissImportState() {
        when (val state = _importState.value) {
            is PluginImportUiState.Preview -> importer.discard(state.preview)
            is PluginImportUiState.Failed, is PluginImportUiState.Installed -> Unit
            else -> return
        }
        _importState.value = PluginImportUiState.Idle
    }

    /**
     * 启用前授予 manifest 声明的全部能力。调用方必须先展示能力清单并取得用户确认。
     */
    fun enablePlugin(pluginId: String) {
        val descriptor = registry.plugin(pluginId) ?: return
        health.clear(pluginId)
        val capabilityIds = descriptor.manifest
            ?.capabilities
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
        grants.grant(pluginId, capabilityIds)
        val result = registry.setEnabled(pluginId, true)
        if (result is PluginActionResult.Failure) {
            grants.revokeAll(pluginId)
        }
    }

    fun disablePlugin(pluginId: String) {
        registry.setEnabled(pluginId, false)
        grants.revokeAll(pluginId)
    }

    fun uninstallPlugin(pluginId: String) {
        grants.revokeAll(pluginId)
        health.clear(pluginId)
        registry.uninstall(pluginId)
    }

    fun reinstallPlugin(pluginId: String) {
        health.clear(pluginId)
        registry.reinstall(pluginId)
    }

    /**
     * 永久删除插件数据，不改变插件的安装/启用状态。由 UI 二次确认后调用。
     */
    suspend fun deletePluginData(pluginId: String): String = withContext(Dispatchers.IO) {
        val descriptor = registry.plugin(pluginId)
            ?: return@withContext "插件不存在：$pluginId"
        val dataSchemaVersion = descriptor.manifest?.dataSchemaVersion ?: 1
        runCatching {
            app.pluginStorageFactory.forPlugin(pluginId, dataSchemaVersion).clear()
        }.fold(
            onSuccess = { "已删除插件数据" },
            onFailure = { error -> "删除失败：${error.message ?: "未知错误"}" },
        )
    }

    fun grantedCapabilities(pluginId: String): Set<String> =
        grants.grantedCapabilities(pluginId)

    fun setStandalone(pluginId: String, standalone: Boolean) {
        tabPreferences.setStandalone(pluginId, standalone)
        _standaloneIds.value = tabPreferences.standalonePluginIds()
    }

    suspend fun dispatchAction(request: PluginActionRequest): ActionExecutionResult =
        app.pluginActions.dispatch(request)

    suspend fun loadPluginData(
        pluginId: String,
        pageId: String,
        sourceId: String,
        params: Map<String, String>,
    ): PluginDataResult = app.pluginDataDispatcher.load(
        PluginPageDataRequest(
            pluginId = pluginId,
            pageId = pageId,
            sourceId = sourceId,
            params = params,
        ),
    )
}
