package com.lifehub.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.ui.theme.LifeHubContentWidth
import com.lifehub.app.ui.theme.LifeHubElevation
import com.lifehub.app.ui.theme.LifeHubSpacing
import com.lifehub.app.backup.BackupPreview
import com.lifehub.app.backup.BackupViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (password.length < 8) {
            showMessage(context.getString(R.string.backup_password_short))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            viewModel.export(password, uri).fold(
                onSuccess = { showMessage(context.getString(R.string.backup_export_success)) },
                onFailure = { error ->
                    showMessage(
                        context.getString(
                            R.string.backup_export_failed,
                            error.message ?: context.getString(R.string.backup_unknown_error),
                        ),
                    )
                },
            )
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (password.isBlank()) {
            showMessage(context.getString(R.string.backup_password_required))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            viewModel.inspect(password, uri).fold(
                onSuccess = { preview = it },
                onFailure = { error ->
                    showMessage(
                        context.getString(
                            R.string.backup_import_failed,
                            error.message ?: context.getString(R.string.backup_unknown_error),
                        ),
                    )
                },
            )
            busy = false
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
                Text(
                    text = stringResource(R.string.backup_description),
                    modifier = Modifier.padding(LifeHubSpacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.backup_password_label)) },
                supportingText = { Text(stringResource(R.string.backup_password_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !busy,
            )

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(LifeHubSpacing.md)) {
                Button(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy && password.length >= 8,
                ) {
                    Text(stringResource(R.string.backup_export))
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy && password.isNotBlank(),
                ) {
                    Text(stringResource(R.string.backup_import))
                }
            }
        }
        }
    }

    preview?.let { pending ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.backup_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LifeHubSpacing.sm)) {
                    Text(
                        stringResource(
                            R.string.backup_preview_message,
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(pending.archive.createdAt)),
                        ),
                    )
                    pending.archive.plugins.forEach { plugin ->
                        Text(
                            stringResource(
                                R.string.backup_preview_plugin,
                                plugin.name,
                                plugin.recordCount,
                                pending.existingRecordCounts[plugin.pluginId] ?: 0,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val importing = pending
                        preview = null
                        scope.launch {
                            busy = true
                            viewModel.apply(importing).fold(
                                onSuccess = {
                                    showMessage(context.getString(R.string.backup_import_success))
                                },
                                onFailure = { error ->
                                    showMessage(
                                        context.getString(
                                            R.string.backup_apply_failed,
                                            error.message
                                                ?: context.getString(R.string.backup_unknown_error),
                                        ),
                                    )
                                },
                            )
                            busy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.backup_preview_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}
