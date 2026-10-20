package com.alabadla.internetcardledger.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.transport.TransportStage
import com.alabadla.internetcardledger.ui.viewmodel.SyncUiState
import com.alabadla.internetcardledger.ui.viewmodel.SyncViewModel

@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onOpenConflictReview: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val approvedDevices by viewModel.approvedDevices.collectAsStateWithLifecycle()
    val unresolvedConflicts by viewModel.unresolvedConflicts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val requiredPermissions = getRequiredPermissions()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.onPermissionsGranted()
        }
    }

    Scaffold(
        topBar = {
            SecondaryTopBar(
                title = stringResource(R.string.title_sync),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.sync_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    DeviceIdentityCard(uiState = uiState)
                }

                item {
                    StageCard(
                        uiState = uiState,
                        onApprove = { endpointId, name ->
                            viewModel.approveConnection(endpointId, name)
                        },
                        onReject = viewModel::rejectConnection,
                        onCancel = viewModel::stopSync,
                    )
                }

                if (unresolvedConflicts.isNotEmpty()) {
                    item {
                        Button(
                            onClick = onOpenConflictReview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.action_review_conflicts,
                                    unresolvedConflicts.size,
                                ),
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.approved_devices_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (approvedDevices.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_approved_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(approvedDevices, key = { it.deviceId }) { device ->
                        ApprovedDeviceCard(
                            device = device,
                            onRemove = { viewModel.removeApprovedDevice(device.deviceId) },
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            val missing = requiredPermissions.filter {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                viewModel.startSync()
                            } else {
                                viewModel.requestStartSync()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(top = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_start_sync),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    if (uiState.showPermissionPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPermissionPrompt,
            title = { Text(stringResource(R.string.sync_permission_dialog_title)) },
            text = { Text(stringResource(R.string.sync_permission_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissPermissionPrompt()
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    },
                ) {
                    Text(stringResource(R.string.action_grant_permissions))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPermissionPrompt) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DeviceIdentityCard(uiState: SyncUiState) {
    val identity = uiState.localIdentity
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val roleText = if (identity?.role == DeviceRole.MAIN) {
                stringResource(R.string.sync_role_main)
            } else {
                stringResource(R.string.sync_role_secondary)
            }
            Text(
                text = identity?.deviceName ?: "هاتف محلي",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = roleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StageCard(
    uiState: SyncUiState,
    onApprove: (endpointId: String, name: String) -> Unit,
    onReject: (endpointId: String) -> Unit,
    onCancel: () -> Unit,
) {
    val tState = uiState.transportState
    val stageText = when (tState.stage) {
        TransportStage.IDLE -> stringResource(R.string.stage_idle)
        TransportStage.SEARCHING -> stringResource(R.string.stage_searching)
        TransportStage.FOUND_DEVICE -> stringResource(R.string.stage_found_device)
        TransportStage.WAITING_APPROVAL -> stringResource(R.string.stage_waiting_approval)
        TransportStage.SENDING_DATA -> stringResource(R.string.stage_sending_data)
        TransportStage.RECEIVING_DATA -> stringResource(R.string.stage_receiving_data)
        TransportStage.SUCCESS -> stringResource(R.string.stage_success)
        TransportStage.FAILED -> stringResource(R.string.stage_failed)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stageText,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (tState.stage in listOf(
                        TransportStage.SEARCHING,
                        TransportStage.SENDING_DATA,
                        TransportStage.RECEIVING_DATA,
                    )
                ) {
                    CircularProgressIndicator()
                }
            }

            tState.discoveredEndpoint?.let { endpoint ->
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "تم العثور على: ${endpoint.deviceName}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "${stringResource(R.string.verification_code_prompt)} ${endpoint.authDigits}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (tState.stage == TransportStage.WAITING_APPROVAL) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onApprove(endpoint.endpointId, endpoint.deviceName) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.action_approve_connection))
                            }
                            OutlinedButton(
                                onClick = { onReject(endpoint.endpointId) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                }
            }

            tState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (tState.stage == TransportStage.SUCCESS) {
                val s = uiState.summary
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.stat_uploaded, s.uploadedCount))
                    Text(stringResource(R.string.stat_downloaded, s.downloadedCount))
                    Text(stringResource(R.string.stat_conflicts, s.conflictsCount))
                }
            }
        }
    }
}

@Composable
private fun ApprovedDeviceCard(
    device: ApprovedDeviceEntity,
    onRemove: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = device.deviceName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "معتمد من الجهاز الرئيسي",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) {
                Text(
                    stringResource(R.string.action_remove_device),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun getRequiredPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.BLUETOOTH)
        add(Manifest.permission.BLUETOOTH_ADMIN)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}
