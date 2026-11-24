package com.lifehub.app.market

import com.lifehub.app.market.index.PluginIndexSource
import com.lifehub.app.market.install.VerifiedApk
import com.lifehub.app.plugin.runtime.index.IndexedPlugin
import com.lifehub.app.plugin.zip.PluginImportPreview

enum class MarketStatus {
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    INDEX_OLDER,
    SIGNER_MISMATCH,
    CONFLICT,
}

data class MarketEntry(
    val plugin: IndexedPlugin,
    val status: MarketStatus,
    val installedVersion: String? = null,
    val note: String? = null,
) {
    val canInstall: Boolean
        get() = status == MarketStatus.NOT_INSTALLED ||
            status == MarketStatus.UPDATE_AVAILABLE
}

enum class MarketPhase { DOWNLOADING, VERIFYING, INSTALLING }

sealed interface MarketOperation {
    data object Idle : MarketOperation

    data class Working(val pluginId: String, val phase: MarketPhase) : MarketOperation

    data class ZipPreview(val preview: PluginImportPreview) : MarketOperation

    data class ApkConfirm(val verified: VerifiedApk) : MarketOperation

    data class NeedsInstallPermission(val verified: VerifiedApk) : MarketOperation

    data class Done(val message: String) : MarketOperation

    data class Failed(val message: String) : MarketOperation
}

data class MarketplaceState(
    val entries: List<MarketEntry> = emptyList(),
    val source: PluginIndexSource = PluginIndexSource.BUNDLED,
    val fetchedAt: Long? = null,
    val offline: Boolean = true,
    val error: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val operation: MarketOperation = MarketOperation.Idle,
)
