package com.lifehub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifehub.app.R
import com.lifehub.app.plugin.PluginImportUiState
import com.lifehub.app.plugin.zip.PluginImportPreview

@Composable
fun PluginImportDialogs(
    state: PluginImportUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        PluginImportUiState.Idle -> Unit
        PluginImportUiState.Inspecting -> ImportProgressDialog(
            title = stringResource(R.string.plugin_import_inspecting),
        )

        PluginImportUiState.Importing -> ImportProgressDialog(
            title = stringResource(R.string.plugin_import_importing),
        )

        is PluginImportUiState.Preview -> ImportPreviewDialog(
            preview = state.preview,
            onConfirm = onConfirm,
            onCancel = onCancel,
        )

        is PluginImportUiState.Installed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.plugin_import_success_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.plugin_import_success_message,
                        state.displayName,
                        state.version,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.plugin_import_done))
                }
            },
        )

        is PluginImportUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.plugin_import_failed_title)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.plugin_import_done))
                }
            },
        )
    }
}

@Composable
private fun ImportProgressDialog(title: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {},
    )
}

@Composable
internal fun ImportPreviewDialog(
    preview: PluginImportPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.plugin_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = preview.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.plugin_detail_version, preview.version))
                Text(stringResource(R.string.plugin_detail_publisher, preview.publisher))
                Text(
                    text = stringResource(R.string.plugin_import_id, preview.pluginId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.plugin_import_type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (preview.isUpdate) {
                    Text(
                        text = stringResource(
                            R.string.plugin_import_update,
                            preview.existingVersion.orEmpty(),
                            preview.version,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = stringResource(R.string.plugin_detail_capabilities),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (preview.capabilities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugin_detail_no_capabilities),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    preview.capabilities.forEach { capability ->
                        Text(
                            text = "• ${capabilityLabel(capability.id)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (capability.reason.isNotBlank()) {
                            Text(
                                text = capability.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = stringResource(
                        if (preview.signerKnown) {
                            R.string.plugin_import_signer_known
                        } else {
                            R.string.plugin_import_signer_new
                        },
                        preview.signerFingerprint,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preview.signerKnown) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.plugin_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.plugin_import_cancel))
            }
        },
    )
}
