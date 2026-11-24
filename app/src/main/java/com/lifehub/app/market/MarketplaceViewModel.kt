package com.lifehub.app.market

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifehub.app.LifeHubApplication
import com.lifehub.app.market.index.PluginIndexSnapshot
import com.lifehub.app.market.install.ApkInstallEvent
import com.lifehub.app.market.install.ApkInstallStatusBus
import com.lifehub.app.market.install.VerifiedApk
import com.lifehub.app.plugin.PluginRegistryState
import com.lifehub.app.plugin.runtime.index.IndexedPlugin
import com.lifehub.app.plugin.runtime.index.PluginIndex
import com.lifehub.app.plugin.runtime.version.PluginVersion
import com.lifehub.app.plugin.zip.PluginImportPreview
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketplaceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LifeHubApplication
    private val registry = app.pluginRegistry
    private val repository = app.pluginIndexRepository
    private val installer = app.marketplaceInstaller

    private val _state = MutableStateFlow(MarketplaceState())
    val state: StateFlow<MarketplaceState> = _state.asStateFlow()

    private var latestIndex: PluginIndex? = null
    private var awaitingInstall = false
    private var pendingPermissionApk: VerifiedApk? = null

    init {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { repository.loadCached() }
            apply(snapshot)
            refresh()
        }
        viewModelScope.launch {
            registry.state.collect { rebuildEntries() }
        }
        viewModelScope.launch {
            ApkInstallStatusBus.events.collect { event ->
                if (event == null) return@collect
                when (event) {
                    is ApkInstallEvent.Success -> {
                        awaitingInstall = false
                        pendingPermissionApk = null
                        registry.reload()
                        rebuildEntries()
                        _state.update {
                            it.copy(operation = MarketOperation.Done("插件已安装"))
                        }
                    }

                    is ApkInstallEvent.Failure -> {
                        awaitingInstall = false
                        _state.update {
                            it.copy(operation = MarketOperation.Failed(event.message))
                        }
                    }
                }
                ApkInstallStatusBus.consume()
            }
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { repository.refresh() }
            apply(snapshot, refreshing = false)
        }
    }

    /** 从系统安装器或权限设置返回时调用。 */
    fun onResume() {
        val pending = pendingPermissionApk
        if (pending != null && installer.canRequestPackageInstalls()) {
            _state.update { it.copy(operation = MarketOperation.ApkConfirm(pending)) }
            return
        }
        if (!awaitingInstall) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.apkPluginSource.refresh() }
            registry.reload()
            rebuildEntries()
            awaitingInstall = false
            if (_state.value.operation is MarketOperation.Working) {
                _state.update { it.copy(operation = MarketOperation.Done("插件已安装")) }
            }
        }
    }

    fun retryPendingApk() {
        val pending = pendingPermissionApk ?: return
        if (installer.canRequestPackageInstalls()) {
            _state.update { it.copy(operation = MarketOperation.ApkConfirm(pending)) }
        }
    }

    fun install(entry: MarketEntry) {
        if (!entry.canInstall) return
        if (_state.value.operation is MarketOperation.Working) return
        if (registry.plugin(entry.plugin.pluginId)?.source == PluginSource.BUILT_IN) {
            _state.update {
                it.copy(operation = MarketOperation.Failed("与内置插件 ID 冲突，不能从市场安装"))
            }
            return
        }
        val pluginId = entry.plugin.pluginId
        _state.update {
            it.copy(operation = MarketOperation.Working(pluginId, MarketPhase.DOWNLOADING))
        }
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                if (entry.plugin.isApk) {
                    installer.prepareApk(entry.plugin).map { Prepared.Apk(it) }
                } else {
                    installer.inspectZip(entry.plugin).map { Prepared.Zip(it) }
                }
            }
            prepared.fold(
                onSuccess = { value ->
                    when (value) {
                        is Prepared.Zip -> _state.update {
                            it.copy(operation = MarketOperation.ZipPreview(value.preview))
                        }

                        is Prepared.Apk -> if (installer.canRequestPackageInstalls()) {
                            _state.update {
                                it.copy(operation = MarketOperation.ApkConfirm(value.verified))
                            }
                        } else {
                            pendingPermissionApk = value.verified
                            _state.update {
                                it.copy(
                                    operation = MarketOperation.NeedsInstallPermission(value.verified),
                                )
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            operation = MarketOperation.Failed(
                                error.message ?: "安装准备失败",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun confirmZipPreview() {
        val preview = (_state.value.operation as? MarketOperation.ZipPreview)?.preview ?: return
        _state.update {
            it.copy(operation = MarketOperation.Working(preview.pluginId, MarketPhase.INSTALLING))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { installer.commitZip(preview) }
            result.fold(
                onSuccess = { installed ->
                    registry.reload()
                    if (registry.plugin(installed.pluginId)?.status == PluginStatus.UNINSTALLED) {
                        registry.reinstall(installed.pluginId)
                    }
                    rebuildEntries()
                    _state.update {
                        it.copy(
                            operation = MarketOperation.Done(
                                "已安装 ${installed.displayName} ${installed.version}",
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(operation = MarketOperation.Failed(error.message ?: "安装失败"))
                    }
                },
            )
        }
    }

    fun cancelZipPreview() {
        val preview = (_state.value.operation as? MarketOperation.ZipPreview)?.preview ?: return
        installer.discardZip(preview)
        _state.update { it.copy(operation = MarketOperation.Idle) }
    }

    fun confirmApk() {
        val verified = (_state.value.operation as? MarketOperation.ApkConfirm)?.verified ?: return
        _state.update {
            it.copy(
                operation = MarketOperation.Working(
                    verified.entry.pluginId,
                    MarketPhase.INSTALLING,
                ),
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { installer.installApk(verified) }
            if (result.isSuccess) {
                awaitingInstall = true
                pendingPermissionApk = null
            } else {
                _state.update {
                    it.copy(
                        operation = MarketOperation.Failed(
                            result.exceptionOrNull()?.message ?: "无法启动系统安装器",
                        ),
                    )
                }
            }
        }
    }

    fun dismissOperation() {
        when (val operation = _state.value.operation) {
            is MarketOperation.ZipPreview -> installer.discardZip(operation.preview)
            is MarketOperation.ApkConfirm -> pendingPermissionApk = null
            is MarketOperation.NeedsInstallPermission -> pendingPermissionApk = null
            else -> Unit
        }
        _state.update { it.copy(operation = MarketOperation.Idle) }
    }

    private fun apply(snapshot: PluginIndexSnapshot, refreshing: Boolean = false) {
        latestIndex = snapshot.index
        _state.update {
            it.copy(
                entries = buildEntries(snapshot.index),
                source = snapshot.source,
                fetchedAt = snapshot.fetchedAt,
                offline = snapshot.offline,
                error = snapshot.error,
                loading = false,
                refreshing = refreshing,
            )
        }
    }

    private fun rebuildEntries() {
        val index = latestIndex ?: return
        _state.update { it.copy(entries = buildEntries(index)) }
    }

    private fun buildEntries(index: PluginIndex): List<MarketEntry> {
        val descriptors = (registry.state.value as? PluginRegistryState.Ready)
            ?.plugins
            .orEmpty()
        return index.plugins.map { plugin ->
            val descriptor = descriptors.firstOrNull { it.pluginId == plugin.pluginId }
            if (descriptor?.source == PluginSource.BUILT_IN) {
                MarketEntry(
                    plugin = plugin,
                    status = MarketStatus.CONFLICT,
                    installedVersion = descriptor.manifest?.version,
                    note = "与内置插件 ID 冲突",
                )
            } else if (plugin.isApk) {
                apkEntry(plugin, descriptor)
            } else {
                zipEntry(plugin, descriptor)
            }
        }
    }

    private fun apkEntry(plugin: IndexedPlugin, descriptor: PluginDescriptor?): MarketEntry {
        if (descriptor?.source == PluginSource.EXTERNAL_ZIP) {
            return MarketEntry(
                plugin = plugin,
                status = MarketStatus.CONFLICT,
                installedVersion = descriptor.manifest?.version,
                note = "已安装同 ID 的声明式插件",
            )
        }
        val handle = app.apkPluginSource.installedHandle(plugin.pluginId)
            ?: return MarketEntry(
                plugin = plugin,
                status = MarketStatus.NOT_INSTALLED,
                installedVersion = descriptor?.manifest?.version,
            )
        val installedVersion = descriptor?.manifest?.version
        val indexVersionCode = plugin.versionCode
        val status = when {
            handle.signerFingerprint != plugin.signerSha256 -> MarketStatus.SIGNER_MISMATCH
            indexVersionCode != null && indexVersionCode > handle.versionCode ->
                MarketStatus.UPDATE_AVAILABLE

            indexVersionCode != null && indexVersionCode < handle.versionCode ->
                MarketStatus.INDEX_OLDER

            else -> MarketStatus.UP_TO_DATE
        }
        return MarketEntry(
            plugin = plugin,
            status = status,
            installedVersion = installedVersion,
            note = if (status == MarketStatus.SIGNER_MISMATCH) "签名者与已安装版本不一致" else null,
        )
    }

    private fun zipEntry(plugin: IndexedPlugin, descriptor: PluginDescriptor?): MarketEntry {
        val installedManifest = descriptor?.manifest
        if (descriptor?.source == PluginSource.EXTERNAL_APK) {
            return MarketEntry(
                plugin = plugin,
                status = MarketStatus.CONFLICT,
                installedVersion = installedManifest?.version,
                note = if (installedManifest != null) {
                    "已安装同 ID 的 APK 插件"
                } else {
                    "存在同 ID 的 APK 插件占位"
                },
            )
        }
        val installedVersion = descriptor?.manifest?.version
            ?: return MarketEntry(plugin, MarketStatus.NOT_INSTALLED)
        val pinned = app.pluginSignerStore.fingerprint(plugin.pluginId)
        if (pinned != null && pinned != plugin.signerSha256) {
            return MarketEntry(
                plugin = plugin,
                status = MarketStatus.SIGNER_MISMATCH,
                installedVersion = installedVersion,
                note = "签名者与已安装版本不一致",
            )
        }
        val status = when {
            PluginVersion.compare(plugin.version, installedVersion) > 0 ->
                MarketStatus.UPDATE_AVAILABLE

            PluginVersion.compare(plugin.version, installedVersion) < 0 ->
                MarketStatus.INDEX_OLDER

            else -> MarketStatus.UP_TO_DATE
        }
        return MarketEntry(plugin, status, installedVersion)
    }

    private sealed interface Prepared {
        data class Apk(val verified: VerifiedApk) : Prepared

        data class Zip(val preview: PluginImportPreview) : Prepared
    }
}
