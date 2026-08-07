package com.markel.flowstate.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.data.backup.RestoreErrorType
import com.markel.flowstate.feature.settings.components.SettingsGroupShapes
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Reset state when entering the screen
    LaunchedEffect(Unit) { viewModel.resetState() }

    // ── Cached JSON between export generation and SAF write ──────────
    var pendingJson by remember { mutableStateOf<String?>(null) }

    // ── SAF launcher for export (CreateDocument) ─────────────────────
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && pendingJson != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingJson!!.toByteArray(Charsets.UTF_8))
                }
                viewModel.onExportSaved()
            } catch (_: Exception) {
                viewModel.onExportCancelled()
            }
        } else {
            viewModel.onExportCancelled()
        }
        pendingJson = null
    }

    // ── SAF launcher for restore (OpenDocument) ──────────────────────
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(it)?.use { input ->
                    input.bufferedReader().readText()
                }
                if (json != null) {
                    viewModel.restoreData(json)
                }
            } catch (_: Exception) {
                // Silently ignore — the ViewModel stays in IDLE
            }
        }
    }

    // ── Observe one-shot events (export JSON ready) ──────────────────
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.ExportReady -> {
                    pendingJson = event.json
                    val timestamp = LocalDateTime.now()
                        .toString()
                        .replace(":", "")
                    exportLauncher.launch("FlowState-Backup-$timestamp.json")
                }
            }
        }
    }

    // ── Scaffold ─────────────────────────────────────────────────────
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.backup_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 60.dp
            ),
        ) {
            // ── Export item ──────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.backup_export),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.backup_export_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.drive_folder_upload_24px),
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            BackupActionButton(
                                state = state.exportState,
                                onIdleClick = { viewModel.startExport() },
                                onSuccessClick = { viewModel.resetState() },
                                onFailureClick = { viewModel.startExport() }
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SettingsGroupShapes.leadingItemShape)
                    )


                    // ── Restore item ─────────────────────────────────────────
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.backup_restore),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.backup_restore_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.download_24px),
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            BackupActionButton(
                                state = state.restoreState,
                                onIdleClick = { restoreLauncher.launch(arrayOf("application/json")) },
                                onSuccessClick = { viewModel.resetState() },
                                onFailureClick = { restoreLauncher.launch(arrayOf("application/json")) },
                                errorType = state.restoreError
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SettingsGroupShapes.endItemShape)
                    )
                }
            }
        }
    }
}

/**
 * A small state-aware action button
 * behavior:
 * - IDLE      → play icon  (tap to start)
 * - IN_PROGRESS → spinner
 * - SUCCESS   → check icon (tap to reset)
 * - FAILURE   → error icon (tap to retry)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupActionButton(
    state: BackupOperationState,
    onIdleClick: () -> Unit,
    onSuccessClick: () -> Unit,
    onFailureClick: () -> Unit,
    errorType: RestoreErrorType? = null
) {
    when (state) {
        BackupOperationState.IDLE -> {
            FilledTonalIconButton(onClick = onIdleClick) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.play_arrow_24px),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        BackupOperationState.IN_PROGRESS -> {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        BackupOperationState.SUCCESS -> {
            FilledTonalIconButton(onClick = onSuccessClick) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        BackupOperationState.FAILURE -> {
            FilledTonalIconButton(onClick = onFailureClick) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.error_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}