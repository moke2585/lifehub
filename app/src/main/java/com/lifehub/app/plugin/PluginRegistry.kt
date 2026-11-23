package com.lifehub.app.plugin

import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginIssue
import com.lifehub.pluginapi.PluginIssueCode
import com.lifehub.pluginapi.PluginLifecycleEvent
import com.lifehub.pluginapi.PluginLifecycleHandler
import com.lifehub.pluginapi.PluginManifestParser
import com.lifehub.pluginapi.PluginManifestValidator
import com.lifehub.pluginapi.PluginPageDocument
import com.lifehub.pluginapi.PluginPageParser
import com.lifehub.pluginapi.PluginPageValidator
import com.lifehub.pluginapi.PluginSchemaLimits
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface PluginRegistryState {
    data object Loading : PluginRegistryState

    data class Ready(val plugins: List<PluginDescriptor>) : PluginRegistryState

    data class Failed(val message: String) : PluginRegistryState
}

sealed interface PluginActionResult {
    data class Success(val descriptor: PluginDescriptor) : PluginActionResult

    data class Failure(val message: String) : PluginActionResult
}

/**
 * 本地插件注册表：发现 APK 内置插件、校验清单、合并持久化状态并派发生命周期事件。
 *
 * 状态切换是幂等的：重复启用/禁用不会重复写盘或重复派发事件。
 */
class PluginRegistry(
    private val packages: PluginPackageSource,
    private val stateStore: PluginStateStore,
    private val hostInfo: HostInfo,
    private val lifecycleHandler: PluginLifecycleHandler = PluginLifecycleHandler { _, _ -> },
    private val trustPolicy: PluginTrustPolicy = PluginTrustPolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow<PluginRegistryState>(PluginRegistryState.Loading)
    val state: StateFlow<PluginRegistryState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun load() {
        if (loaded) return

        runCatching { withContext(ioDispatcher) { discoverPlugins() } }
            .onSuccess { plugins ->
                loaded = true
                _state.value = PluginRegistryState.Ready(plugins)
            }
            .onFailure { error ->
                _state.value = PluginRegistryState.Failed(
                    "插件列表加载失败：${error.message ?: "未知错误"}",
                )
            }
    }

    /** 导入、卸载或升级外部插件后重新发现插件。 */
    suspend fun reload() {
        loaded = false
        load()
    }

    @Synchronized
    fun setEnabled(pluginId: String, enabled: Boolean): PluginActionResult {
        val ready = _state.value as? PluginRegistryState.Ready
            ?: return PluginActionResult.Failure("插件列表尚未加载完成")
        val current = ready.plugins.firstOrNull { it.pluginId == pluginId }
            ?: return PluginActionResult.Failure("插件不存在：$pluginId")

        if (!current.canToggle) {
            return PluginActionResult.Failure("插件当前状态不可切换：${current.status}")
        }

        val target = if (enabled) PluginStatus.ENABLED else PluginStatus.DISABLED
        if (current.status == target) return PluginActionResult.Success(current)

        stateStore.write(pluginId, target)
        val updated = current.copy(status = target)
        _state.value = ready.copy(
            plugins = ready.plugins.map { if (it.pluginId == pluginId) updated else it },
        )
        dispatch(pluginId, if (enabled) PluginLifecycleEvent.ENABLE else PluginLifecycleEvent.DISABLE)
        return PluginActionResult.Success(updated)
    }

    fun plugin(pluginId: String): PluginDescriptor? =
        (_state.value as? PluginRegistryState.Ready)
            ?.plugins
            ?.firstOrNull { it.pluginId == pluginId }

    /**
     * 卸载只移除入口并保留数据；重新安装恢复默认的 INSTALLED 状态，数据仍可读。
     */
    @Synchronized
    fun uninstall(pluginId: String): PluginActionResult {
        val ready = _state.value as? PluginRegistryState.Ready
            ?: return PluginActionResult.Failure("插件列表尚未加载完成")
        val current = ready.plugins.firstOrNull { it.pluginId == pluginId }
            ?: return PluginActionResult.Failure("插件不存在：$pluginId")
        if (current.status == PluginStatus.UNINSTALLED) {
            return PluginActionResult.Success(current)
        }
        if (current.status == PluginStatus.INCOMPATIBLE || current.status == PluginStatus.ERROR) {
            return PluginActionResult.Failure("插件当前状态不可卸载：${current.status}")
        }

        stateStore.write(pluginId, PluginStatus.UNINSTALLED)
        val updated = current.copy(status = PluginStatus.UNINSTALLED)
        _state.value = ready.copy(
            plugins = ready.plugins.map { if (it.pluginId == pluginId) updated else it },
        )
        dispatch(pluginId, PluginLifecycleEvent.UNINSTALL)
        return PluginActionResult.Success(updated)
    }

    @Synchronized
    fun reinstall(pluginId: String): PluginActionResult {
        val ready = _state.value as? PluginRegistryState.Ready
            ?: return PluginActionResult.Failure("插件列表尚未加载完成")
        val current = ready.plugins.firstOrNull { it.pluginId == pluginId }
            ?: return PluginActionResult.Failure("插件不存在：$pluginId")
        if (current.status != PluginStatus.UNINSTALLED) {
            return PluginActionResult.Failure("插件未处于卸载状态：$pluginId")
        }

        stateStore.clear(pluginId)
        val updated = current.copy(status = PluginStatus.INSTALLED)
        _state.value = ready.copy(
            plugins = ready.plugins.map { if (it.pluginId == pluginId) updated else it },
        )
        dispatch(pluginId, PluginLifecycleEvent.INSTALL)
        return PluginActionResult.Success(updated)
    }

    private fun discoverPlugins(): List<PluginDescriptor> {
        val folderIds = packages.pluginIds().sorted()
        val denied = mutableListOf<String>()
        val trusted = folderIds.filter { folderId ->
            when (val decision = trustPolicy.verify(packages.trustFor(folderId))) {
                PluginTrustDecision.Trusted -> true

                is PluginTrustDecision.Denied -> {
                    denied += folderId
                    log("plugin_package_denied pluginId=$folderId reason=${decision.reason}")
                    false
                }
            }
        }
        if (folderIds.isNotEmpty() && trusted.isEmpty()) {
            error("没有可信的插件包来源：${denied.joinToString()}")
        }

        val seenIds = mutableSetOf<String>()
        return trusted
            .mapNotNull { folderId ->
                loadPlugin(
                    folderId = folderId,
                    seenIds = seenIds,
                    source = sourceOf(packages.trustFor(folderId)),
                )
            }
            .sortedBy { it.pluginId }
            .distinctBy { it.pluginId }
    }

    private fun sourceOf(trust: PluginPackageTrust): PluginSource = when (trust) {
        PluginPackageTrust.ZIP_SIGNED -> PluginSource.EXTERNAL_ZIP
        else -> PluginSource.BUILT_IN
    }

    private fun loadPlugin(
        folderId: String,
        seenIds: MutableSet<String>,
        source: PluginSource,
    ): PluginDescriptor? {
        val manifestText = packages.open(folderId, MANIFEST_FILE)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return errorDescriptor(folderId, "缺少 $MANIFEST_FILE", source)

        val manifest = PluginManifestParser.parse(manifestText).getOrElse { error ->
            return errorDescriptor(
                folderId,
                "manifest 无法解析：${error.message ?: "未知错误"}",
                source,
            )
        }

        if (!seenIds.add(manifest.pluginId)) {
            log("duplicate_plugin_id pluginId=${manifest.pluginId} folder=$folderId")
            return null
        }

        val issues = mutableListOf<PluginIssue>()
        issues += PluginManifestValidator.validate(manifest, hostInfo)

        if (manifest.pluginId != folderId) {
            issues += PluginIssue(
                PluginIssueCode.PLUGIN_ID_MISMATCH,
                "插件目录名 $folderId 与 pluginId ${manifest.pluginId} 不一致",
            )
        }

        val pageIds = manifest.pages.map { it.id }.toSet()
        val pageDocuments = mutableMapOf<String, PluginPageDocument>()
        manifest.pages.forEach { page ->
            when (val read = readPageText(folderId, page.file)) {
                PageRead.Missing -> issues += PluginIssue(
                    PluginIssueCode.MISSING_PAGE_FILE,
                    "页面文件不存在：${page.file}",
                )

                is PageRead.Failure -> issues += PluginIssue(
                    PluginIssueCode.INVALID_SCHEMA,
                    "页面 ${page.id}：${read.message}",
                )

                is PageRead.Success -> PluginPageParser.parse(read.text)
                    .onSuccess { document ->
                        val pageIssues = PluginPageValidator.validate(document, page.id, pageIds)
                        issues += pageIssues
                        if (pageIssues.isEmpty()) {
                            pageDocuments[page.id] = document
                        }
                    }
                    .onFailure { error ->
                        issues += PluginIssue(
                            PluginIssueCode.INVALID_SCHEMA,
                            "页面 ${page.id} JSON 无法解析：${error.message ?: "未知错误"}",
                        )
                    }
            }
        }

        val status = if (issues.isNotEmpty()) {
            PluginStatus.INCOMPATIBLE
        } else {
            when (stateStore.read(manifest.pluginId)) {
                PluginStatus.ENABLED -> PluginStatus.ENABLED
                PluginStatus.DISABLED -> PluginStatus.DISABLED
                PluginStatus.UNINSTALLED -> PluginStatus.UNINSTALLED
                else -> PluginStatus.INSTALLED
            }
        }

        return PluginDescriptor(
            pluginId = manifest.pluginId,
            displayName = manifest.name,
            manifest = manifest,
            status = status,
            source = source,
            issues = issues,
            pages = pageDocuments,
        )
    }

    private fun errorDescriptor(
        folderId: String,
        message: String,
        source: PluginSource,
    ): PluginDescriptor =
        PluginDescriptor(
            pluginId = folderId,
            displayName = folderId,
            manifest = null,
            status = PluginStatus.ERROR,
            source = source,
            issues = listOf(PluginIssue(PluginIssueCode.MANIFEST_UNREADABLE, message)),
        )

    private fun dispatch(pluginId: String, event: PluginLifecycleEvent) {
        runCatching { lifecycleHandler.onEvent(pluginId, event) }
            .onFailure { error ->
                log(
                    "lifecycle_failed pluginId=$pluginId event=${event.name} " +
                        "error=${error.javaClass.simpleName}",
                )
            }
    }

    private fun readPageText(folderId: String, file: String): PageRead {
        val stream = packages.open(folderId, file) ?: return PageRead.Missing
        return try {
            stream.use { input ->
                // InputStream.readNBytes 需要 API 33，这里手写有界读取以兼容 minSdk 26。
                val limit = PluginSchemaLimits.MAX_PAGE_BYTES
                val buffer = ByteArray(limit + 1)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                if (total > limit) {
                    PageRead.Failure("页面文件超过 ${limit / 1024} KB 上限")
                } else {
                    PageRead.Success(String(buffer, 0, total, Charsets.UTF_8))
                }
            }
        } catch (error: Exception) {
            PageRead.Failure("读取页面失败：${error.message ?: "未知错误"}")
        }
    }

    private sealed interface PageRead {
        data class Success(val text: String) : PageRead

        data object Missing : PageRead

        data class Failure(val message: String) : PageRead
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}
