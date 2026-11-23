package com.lifehub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifehub.app.R
import com.lifehub.app.ui.theme.LifeHubContentWidth
import com.lifehub.app.ui.theme.LifeHubElevation
import com.lifehub.app.ui.theme.LifeHubSpacing
import com.lifehub.pluginapi.PluginCapability
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginIssueCode
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailScreen(
    descriptor: PluginDescriptor,
    grantedCapabilities: Set<String>,
    standalone: Boolean,
    quarantined: Boolean,
    onStandaloneChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenPage: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onReinstall: () -> Unit,
    onUninstall: () -> Unit,
    onDeleteData: suspend () -> String,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showUninstallDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val upgradeRequired = descriptor.issues.any {
        it.code == PluginIssueCode.HOST_VERSION_TOO_LOW ||
            it.code == PluginIssueCode.UNSUPPORTED_PLUGIN_SDK
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = descriptor.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.error_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = LifeHubContentWidth.maxContent)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(LifeHubSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.lg),
            ) {
            if (quarantined) {
                DetailNoticeCard(
                    title = stringResource(R.string.plugin_quarantined_title),
                    message = stringResource(R.string.plugin_quarantined_message),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (upgradeRequired) {
                DetailNoticeCard(
                    title = stringResource(R.string.plugin_upgrade_required_title),
                    message = stringResource(R.string.plugin_upgrade_required_message),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            StatusPill(descriptor.status)

            val manifest = descriptor.manifest
            if (manifest == null) {
                Text(
                    text = if (descriptor.status == PluginStatus.NOT_INSTALLED) {
                        stringResource(R.string.plugin_not_installed_message)
                    } else {
                        stringResource(R.string.plugin_manifest_unreadable_message)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = manifest.description.ifBlank {
                        stringResource(R.string.plugin_detail_description_missing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                val sourceLabel = when (descriptor.source) {
                    PluginSource.BUILT_IN -> stringResource(R.string.plugin_source_built_in)
                    PluginSource.EXTERNAL_ZIP -> stringResource(R.string.plugin_source_external_zip)
                    PluginSource.EXTERNAL_APK -> stringResource(R.string.plugin_source_external_apk)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = LifeHubElevation.card,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(LifeHubSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
                    ) {
                        Text(
                            text = stringResource(R.string.plugin_detail_version, manifest.version),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.plugin_detail_publisher, manifest.publisher),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.plugin_detail_source, sourceLabel),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.plugin_detail_sdk, manifest.pluginSdkVersion),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.plugin_detail_host, manifest.minHostVersion),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.plugin_detail_data_schema,
                                manifest.dataSchemaVersion,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.plugin_detail_capabilities),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (manifest.capabilities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugin_detail_no_capabilities),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    manifest.capabilities.forEach { capability ->
                        CapabilityItem(
                            capability = capability,
                            granted = capability.id in grantedCapabilities,
                        )
                    }
                }
            }

            if (descriptor.issues.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.plugin_detail_issues),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                descriptor.issues.forEach { issue ->
                    Text(
                        text = "• ${issue.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (descriptor.status == PluginStatus.ENABLED) {
                Card {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_standalone_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.plugin_standalone_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = standalone,
                            onCheckedChange = onStandaloneChange,
                        )
                    }
                }
            }

            if (descriptor.status == PluginStatus.UNINSTALLED) {
                Row(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
                    Button(
                        onClick = onReinstall,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.plugin_detail_reinstall))
                    }
                    FilledTonalButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.plugin_detail_delete_data))
                    }
                }
            } else {
                if (descriptor.canToggle || descriptor.isOpenable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
                        if (descriptor.isOpenable) {
                            Button(
                                onClick = onOpenPage,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.plugin_detail_open))
                            }
                        }
                        if (descriptor.canToggle) {
                            val enabling = descriptor.status != PluginStatus.ENABLED
                            if (enabling) {
                                Button(
                                    onClick = { onToggle(true) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.plugin_detail_enable))
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { onToggle(false) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.plugin_detail_disable))
                                }
                            }
                        }
                    }
                }

                if (descriptor.canToggle) {
                    TextButton(onClick = { showUninstallDialog = true }) {
                        Text(stringResource(R.string.plugin_detail_uninstall))
                    }
                }
                TextButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.plugin_detail_delete_data))
                }
            }
        }
        }
    }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text(stringResource(R.string.plugin_uninstall_title)) },
            text = {
                Text(
                    stringResource(R.string.plugin_uninstall_message, descriptor.displayName),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallDialog = false
                        onUninstall()
                    },
                ) {
                    Text(stringResource(R.string.plugin_uninstall_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.plugin_delete_data_title)) },
            text = {
                Text(
                    stringResource(R.string.plugin_delete_data_message, descriptor.displayName),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(onDeleteData())
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.plugin_delete_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailNoticeCard(
    title: String,
    message: String,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(LifeHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun StatusPill(status: PluginStatus) {
    val color = pluginStatusColor(status)
    Text(
        text = pluginStatusLabel(status),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = LifeHubSpacing.sm, vertical = LifeHubSpacing.xs),
    )
}

@Composable
private fun CapabilityItem(capability: PluginCapability, granted: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.xxs)) {
        Text(
            text = capabilityLabel(capability.id),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = capability.id,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (granted) R.string.capability_granted else R.string.capability_not_granted,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (capability.reason.isNotBlank()) {
            Text(
                text = capability.reason,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
