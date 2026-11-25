package com.lifehub.app

import android.app.Application
import com.lifehub.app.backup.PluginBackupManager
import com.lifehub.app.backup.ExternalPluginBackup
import com.lifehub.app.capability.PluginCapabilityBroker
import com.lifehub.app.capability.PluginCapabilityGrantStore
import com.lifehub.app.capability.SharedPreferencesCapabilityGrantStore
import com.lifehub.app.core.AppLog
import com.lifehub.app.data.PluginStorageFactory
import com.lifehub.app.data.SqlitePluginStorageFactory
import com.lifehub.app.market.download.ArtifactDownloader
import com.lifehub.app.market.index.BundledPluginIndex
import com.lifehub.app.market.index.HttpIndexClient
import com.lifehub.app.market.index.PluginIndexCache
import com.lifehub.app.market.index.PluginIndexRepository
import com.lifehub.app.market.install.MarketplaceInstaller
import com.lifehub.app.plugin.AssetPluginPackageSource
import com.lifehub.app.plugin.CompositePluginPackageSource
import com.lifehub.app.plugin.PluginActionDispatcher
import com.lifehub.app.plugin.PluginDataDispatcher
import com.lifehub.app.plugin.PluginHealthMonitor
import com.lifehub.app.plugin.PluginPackageSource
import com.lifehub.app.plugin.PluginRegistry
import com.lifehub.app.plugin.PluginRegistryState
import com.lifehub.app.plugin.PluginTabPreferenceStore
import com.lifehub.app.plugin.SharedPreferencesPluginHealthStore
import com.lifehub.app.plugin.SharedPreferencesPluginStateStore
import com.lifehub.app.plugin.SharedPreferencesPluginTabPreferenceStore
import com.lifehub.app.plugin.ZipPluginPackageSource
import com.lifehub.app.plugin.apk.ApkPluginPackageSource
import com.lifehub.app.plugin.apk.LedgerApkMigration
import com.lifehub.app.plugin.apk.SharedPreferencesMigrationStateStore
import com.lifehub.app.plugin.zip.PluginZipImporter
import com.lifehub.app.plugin.zip.SharedPreferencesPluginSignerStore
import com.lifehub.app.plugins.FirstPartyPluginHandlers
import com.lifehub.app.plugins.LedgerPlugin
import com.lifehub.app.plugins.TravelPlugin
import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginApiVersion
import com.lifehub.pluginapi.PluginLifecycleHandler
import com.lifehub.pluginapi.PluginPathRules
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifeHubApplication : Application() {

    private val hostInfo: HostInfo
        get() = HostInfo(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            pluginApiMajor = PluginApiVersion.MAJOR,
        )

    private val builtInPluginSource: AssetPluginPackageSource by lazy {
        AssetPluginPackageSource(
            assets = assets,
            excludedPluginIds = { ledgerApkMigration.readyPluginIds() },
        )
    }

    private val externalPluginSource: ZipPluginPackageSource by lazy {
        ZipPluginPackageSource(pluginsDir)
    }

    val apkPluginSource: ApkPluginPackageSource by lazy {
        ApkPluginPackageSource(
            context = this,
            log = { message -> AppLog.event(AppLog.Category.PLUGIN, message) },
        )
    }

    val ledgerApkMigration: LedgerApkMigration by lazy {
        LedgerApkMigration(
            state = SharedPreferencesMigrationStateStore(
                getSharedPreferences(MIGRATION_PREFERENCES, MODE_PRIVATE),
            ),
            storageFactory = pluginStorageFactory,
            installedFirstInstallTime = {
                apkPluginSource.installedHandle(LedgerPlugin.PLUGIN_ID)?.firstInstallTime
            },
            exportPayload = { apkPluginSource.exportBackup(LedgerPlugin.PLUGIN_ID) },
            importPayload = { payload ->
                apkPluginSource.importBackup(LedgerPlugin.PLUGIN_ID, payload)
            },
            log = { message -> AppLog.event(AppLog.Category.PLUGIN, message) },
        )
    }

    val pluginPackages: PluginPackageSource by lazy {
        CompositePluginPackageSource(
            listOf(builtInPluginSource, externalPluginSource, apkPluginSource),
        )
    }

    private val bundledPluginIndex: BundledPluginIndex by lazy {
        BundledPluginIndex(assets)
    }

    val pluginIndexRepository: PluginIndexRepository by lazy {
        PluginIndexRepository(
            client = HttpIndexClient(),
            cache = PluginIndexCache(File(filesDir, MARKET_INDEX_DIR)),
            bundledRegistry = bundledPluginIndex::registryBytes,
            publicKey = bundledPluginIndex::publicKey,
            indexUrl = BuildConfig.PLUGIN_INDEX_URL,
            signatureUrl = BuildConfig.PLUGIN_INDEX_SIGNATURE_URL,
        )
    }

    private val pluginStateStore: SharedPreferencesPluginStateStore by lazy {
        SharedPreferencesPluginStateStore(
            getSharedPreferences(PLUGIN_STATE_PREFERENCES, MODE_PRIVATE),
        )
    }

    val pluginRegistry: PluginRegistry by lazy {
        PluginRegistry(
            packages = pluginPackages,
            stateStore = pluginStateStore,
            hostInfo = hostInfo,
            lifecycleHandler = PluginLifecycleHandler { pluginId, event ->
                AppLog.event(AppLog.Category.PLUGIN, "lifecycle_${event.name}", pluginId)
            },
            log = { message -> AppLog.event(AppLog.Category.PLUGIN, message) },
        )
    }

    val pluginSignerStore: SharedPreferencesPluginSignerStore by lazy {
        SharedPreferencesPluginSignerStore(
            getSharedPreferences(SIGNER_PREFERENCES, MODE_PRIVATE),
        )
    }

    val pluginZipImporter: PluginZipImporter by lazy {
        PluginZipImporter(
            pluginsDir = pluginsDir,
            importDir = File(filesDir, IMPORT_DIR),
            hostInfo = hostInfo,
            signerStore = pluginSignerStore,
            // 内置插件与已安装 APK 插件都保留自己的 ID，ZIP 不能覆盖。
            builtInPluginIds = {
                (builtInPluginSource.pluginIds() + apkPluginSource.pluginIds()).toSet()
            },
        )
    }

    val artifactDownloader: ArtifactDownloader by lazy {
        ArtifactDownloader(File(cacheDir, MARKET_DOWNLOAD_DIR))
    }

    val marketplaceInstaller: MarketplaceInstaller by lazy {
        MarketplaceInstaller(
            context = this,
            downloader = artifactDownloader,
            zipImporter = pluginZipImporter,
            apkPluginSource = apkPluginSource,
        )
    }

    private val pluginsDir: File
        get() = File(filesDir, EXTERNAL_PLUGIN_DIR)

    val capabilityGrants: PluginCapabilityGrantStore by lazy {
        SharedPreferencesCapabilityGrantStore(
            getSharedPreferences(CAPABILITY_PREFERENCES, MODE_PRIVATE),
        )
    }

    val pluginTabPreferences: PluginTabPreferenceStore by lazy {
        SharedPreferencesPluginTabPreferenceStore(
            getSharedPreferences(TAB_PREFERENCES, MODE_PRIVATE),
        )
    }

    val pluginHealthMonitor: PluginHealthMonitor by lazy {
        PluginHealthMonitor(
            store = SharedPreferencesPluginHealthStore(
                getSharedPreferences(HEALTH_PREFERENCES, MODE_PRIVATE),
            ),
            onQuarantine = { pluginId ->
                pluginRegistry.setEnabled(pluginId, false)
                capabilityGrants.revokeAll(pluginId)
                AppLog.event(AppLog.Category.PLUGIN, "plugin_quarantined", pluginId)
            },
        )
    }

    val pluginStorageFactory: PluginStorageFactory by lazy {
        SqlitePluginStorageFactory(
            context = this,
            migrations = mapOf(TravelPlugin.PLUGIN_ID to TravelPlugin.migration),
            onMigrationFailure = { pluginId, error ->
                AppLog.error(
                    category = AppLog.Category.DATA,
                    event = "data_migration_failed",
                    errorCode = pluginId,
                    throwable = error,
                )
                pluginHealthMonitor.quarantine(pluginId)
            },
        )
    }

    val capabilityBroker: PluginCapabilityBroker by lazy {
        PluginCapabilityBroker(
            descriptorProvider = { pluginId -> pluginRegistry.plugin(pluginId) },
            grants = capabilityGrants,
        )
    }

    // 只读插件包内资产，用于内置地图与图片导出等静态数据；同时支持内置与外部 ZIP。
    private val pluginAssetReader: (String, String) -> String? = { pluginId, path ->
        if (!PluginPathRules.isSafeRelativePath(path)) {
            null
        } else {
            runCatching {
                pluginPackages.open(pluginId, path)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            }.getOrNull()
        }
    }

    val pluginActions: PluginActionDispatcher by lazy {
        PluginActionDispatcher(
            handlers = FirstPartyPluginHandlers.actionHandlers,
            capabilities = capabilityBroker,
            storages = pluginStorageFactory,
            health = pluginHealthMonitor,
            assetReader = pluginAssetReader,
            external = apkPluginSource::dispatchAction,
        )
    }

    val pluginDataDispatcher: PluginDataDispatcher by lazy {
        PluginDataDispatcher(
            handlers = FirstPartyPluginHandlers.dataHandlers,
            capabilities = capabilityBroker,
            storages = pluginStorageFactory,
            health = pluginHealthMonitor,
            assetReader = pluginAssetReader,
            external = apkPluginSource::dispatchData,
        )
    }

    val backupManager: PluginBackupManager by lazy {
        PluginBackupManager(
            pluginProvider = {
                (pluginRegistry.state.value as? PluginRegistryState.Ready)?.plugins.orEmpty()
            },
            storageProvider = { descriptor ->
                pluginStorageFactory.forPlugin(
                    descriptor.pluginId,
                    descriptor.manifest?.dataSchemaVersion ?: 1,
                )
            },
            appVersion = BuildConfig.VERSION_NAME,
            hostVersionCode = BuildConfig.VERSION_CODE,
            externalBackup = object : ExternalPluginBackup {
                override fun export(pluginId: String): String? =
                    apkPluginSource.exportBackup(pluginId)

                override fun import(pluginId: String, payload: String) {
                    apkPluginSource.importBackup(pluginId, payload).getOrThrow()
                }

                override fun recordCount(pluginId: String): Int? =
                    apkPluginSource.backupRecordCount(pluginId)
            },
        )
    }

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.event(AppLog.Category.STARTUP, "application_created")
        migrationScope.launch {
            ledgerApkMigration.migrateIfNeeded()
                .onSuccess { outcome ->
                    if (outcome.changed) pluginRegistry.reload()
                }
        }
    }

    private companion object {
        const val PLUGIN_STATE_PREFERENCES = "plugin_state"
        const val CAPABILITY_PREFERENCES = "plugin_capabilities"
        const val TAB_PREFERENCES = "plugin_tabs"
        const val HEALTH_PREFERENCES = "plugin_health"
        const val SIGNER_PREFERENCES = "plugin_signers"
        const val MIGRATION_PREFERENCES = "plugin_migrations"
        const val EXTERNAL_PLUGIN_DIR = "plugins"
        const val IMPORT_DIR = "plugin_import"
        const val MARKET_INDEX_DIR = "market_index"
        const val MARKET_DOWNLOAD_DIR = "market_downloads"
    }
}
