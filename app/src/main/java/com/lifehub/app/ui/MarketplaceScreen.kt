package com.lifehub.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.market.MarketEntry
import com.lifehub.app.market.MarketOperation
import com.lifehub.app.market.MarketPhase
import com.lifehub.app.market.MarketStatus
import com.lifehub.app.market.MarketplaceState
import com.lifehub.app.market.MarketplaceViewModel
import com.lifehub.app.market.index.PluginIndexSource
import com.lifehub.app.market.install.VerifiedApk
import com.lifehub.app.plugin.runtime.index.IndexedPlugin
import com.lifehub.app.ui.theme.LifeHubSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarketplaceRoute(
    initialPluginId: String?,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MarketplaceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    BackHandler(onBack = onBack)

    val entry = initialPluginId?.let { id ->
        state.entries.firstOrNull { it.plugin.pluginId == id }
    }
    if (initialPluginId == null) {
        MarketplaceScreen(
            state = state,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onOpenEntry = onOpenDetail,
            onInstall = viewModel::install,
        )
    } else if (entry == null) {
        ErrorScreen(
            title = stringResource(R.string.plugin_missing_title),
            message = stringResource(R.string.plugin_missing_message),
            onBack = onBack,
        )
    } else {
        MarketplaceDetailScreen(
            entry = entry,
            state = state,
            onBack = onBack,
            onInstall = { viewModel.install(entry) },
        )
    }

    MarketplaceDialogs(
        operation = state.operation,
        onConfirmZip = viewModel::confirmZipPreview,
        onCancelZip = viewModel::cancelZipPreview,
        onConfirmApk = viewModel::confirmApk,
        onDismiss = viewModel::dismissOperation,
    )
}

@Composable
private fun MarketplaceScreen(
    state: MarketplaceState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onInstall: (MarketEntry) -> Unit,
) {
    Scaffold(
        topBar = {
            LifeHubTopBar(
                title = { LifeHubTopBarTitle(stringResource(R.string.market_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.market_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.market_refresh),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.size(LifeHubSpacing.md))
                    Text(stringResource(R.string.market_loading))
                }
            }

            state.entries.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        start = LifeHubSpacing.lg,
                        end = LifeHubSpacing.lg,
                        top = LifeHubSpacing.md,
                    ),
            ) {
                IndexStatusBanner(state)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = LifeHubSpacing.xl),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_market),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.size(LifeHubSpacing.md))
                        Text(
                            text = stringResource(R.string.market_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.size(LifeHubSpacing.xs))
                        Text(
                            text = stringResource(R.string.market_empty_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(LifeHubSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
            ) {
                item { IndexStatusBanner(state) }
                items(state.entries, key = { it.plugin.pluginId }) { entry ->
                    MarketEntryCard(
                        entry = entry,
                        onClick = { onOpenEntry(entry.plugin.pluginId) },
                        onInstall = { onInstall(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexStatusBanner(state: MarketplaceState) {
    val status = when {
        state.source == PluginIndexSource.BUNDLED -> stringResource(R.string.market_status_bundled)
        state.offline -> stringResource(R.string.market_status_offline)
        state.source == PluginIndexSource.CACHE -> stringResource(R.string.market_status_cache)
        else -> stringResource(R.string.market_status_updated)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.fetchedAt?.let { fetchedAt ->
            val formatted = remember(fetchedAt) { formatTime(fetchedAt) }
            Text(
                text = stringResource(R.string.market_updated_at, formatted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { error ->
            Text(
                text = stringResource(R.string.market_error, error),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MarketEntryCard(
    entry: MarketEntry,
    onClick: () -> Unit,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifeHubSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_market),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
            ) {
                Text(
                    text = entry.plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.market_version, entry.plugin.version) +
                        " · " + artifactLabel(entry.plugin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.installedVersion?.let { installed ->
                    Text(
                        text = stringResource(R.string.market_installed_version, installed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = capabilitySummary(entry.plugin.capabilities),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
            ) {
                StatusChip(entry.status)
                if (entry.canInstall) {
                    if (entry.status == MarketStatus.UPDATE_AVAILABLE) {
                        FilledTonalButton(onClick = onInstall) {
                            Text(stringResource(R.string.market_update))
                        }
                    } else {
                        Button(onClick = onInstall) {
                            Text(stringResource(R.string.market_install))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceDetailScreen(
    entry: MarketEntry,
    state: MarketplaceState,
    onBack: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            LifeHubTopBar(
                title = { LifeHubTopBarTitle(entry.plugin.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.market_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_market),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs),
                ) {
                    Text(
                        text = stringResource(R.string.market_version, entry.plugin.version) +
                            " · " + artifactLabel(entry.plugin),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.installedVersion?.let { installed ->
                        Text(
                            text = stringResource(R.string.market_installed_version, installed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusChip(entry.status)
            }

            entry.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.error?.let { error ->
                Text(
                    text = stringResource(R.string.market_error, error),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.market_detail_capabilities),
                style = MaterialTheme.typography.titleSmall,
            )
            for (capability in entry.plugin.capabilities) {
                Text(
                    text = "• ${capabilityLabel(capability)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoRow(
                label = stringResource(R.string.market_detail_signer),
                value = entry.plugin.signerSha256,
            )
            InfoRow(
                label = stringResource(R.string.market_detail_application_id),
                value = entry.plugin.applicationId ?: "—",
            )
            entry.plugin.sizeBytes?.let { bytes ->
                InfoRow(
                    label = stringResource(R.string.market_detail_size),
                    value = formatSize(bytes),
                )
            }
            InfoRow(
                label = stringResource(R.string.market_detail_source),
                value = entry.plugin.url,
            )
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(entry.plugin.repo)),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.market_detail_repo) + " · " + entry.plugin.repo)
            }

            if (entry.canInstall) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (entry.status == MarketStatus.UPDATE_AVAILABLE) {
                                R.string.market_update
                            } else {
                                R.string.market_install
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusChip(status: MarketStatus) {
    val style = when (status) {
        MarketStatus.NOT_INSTALLED -> ChipStyle(
            R.string.market_not_installed,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )

        MarketStatus.UPDATE_AVAILABLE -> ChipStyle(
            R.string.market_update,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )

        MarketStatus.UP_TO_DATE -> ChipStyle(
            R.string.market_up_to_date,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MarketStatus.INDEX_OLDER -> ChipStyle(
            R.string.market_index_older,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MarketStatus.SIGNER_MISMATCH -> ChipStyle(
            R.string.market_signer_mismatch,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        MarketStatus.CONFLICT -> ChipStyle(
            R.string.market_conflict,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Surface(shape = CircleShape, color = style.container) {
        Text(
            text = stringResource(style.labelRes),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = style.content,
        )
    }
}

@Composable
private fun MarketplaceDialogs(
    operation: MarketOperation,
    onConfirmZip: () -> Unit,
    onCancelZip: () -> Unit,
    onConfirmApk: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (operation) {
        MarketOperation.Idle -> Unit

        is MarketOperation.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.market_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(
                            if (operation.phase == MarketPhase.INSTALLING) {
                                R.string.market_working_installing
                            } else {
                                R.string.market_working_downloading
                            },
                        ),
                    )
                }
            },
            confirmButton = {},
        )

        is MarketOperation.ZipPreview -> ImportPreviewDialog(
            preview = operation.preview,
            onConfirm = onConfirmZip,
            onCancel = onCancelZip,
        )

        is MarketOperation.ApkConfirm -> ApkConfirmDialog(
            verified = operation.verified,
            onConfirm = onConfirmApk,
            onDismiss = onDismiss,
        )

        is MarketOperation.NeedsInstallPermission -> PermissionDialog(onDismiss = onDismiss)

        is MarketOperation.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.market_title)) },
            text = { Text(operation.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.market_done))
                }
            },
        )

        is MarketOperation.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.market_failed_title)) },
            text = { Text(operation.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.market_done))
                }
            },
        )
    }
}

@Composable
private fun ApkConfirmDialog(
    verified: VerifiedApk,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_apk_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs)) {
                Text(
                    stringResource(
                        R.string.market_apk_confirm_message,
                        verified.entry.name,
                        verified.entry.version,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.market_detail_capabilities) +
                        "：" + capabilitySummary(verified.entry.capabilities),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.market_detail_signer) +
                        "：" + verified.signerFingerprint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.market_detail_source) +
                        "：" + verified.entry.repo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.market_apk_confirm_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.market_cancel))
            }
        },
    )
}

@Composable
private fun PermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_permission_title)) },
        text = { Text(stringResource(R.string.market_permission_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.market_permission_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.market_permission_later))
            }
        },
    )
}

@Composable
private fun artifactLabel(plugin: IndexedPlugin): String =
    stringResource(
        if (plugin.isApk) R.string.market_artifact_apk else R.string.market_artifact_zip,
    )

@Composable
private fun capabilitySummary(ids: List<String>): String {
    val labels = mutableListOf<String>()
    for (id in ids) {
        labels += capabilityLabel(id)
    }
    return labels.joinToString(" · ")
}

private fun formatTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private data class ChipStyle(
    val labelRes: Int,
    val container: Color,
    val content: Color,
)
