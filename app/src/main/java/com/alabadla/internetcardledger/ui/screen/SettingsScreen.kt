package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.data.repository.BackupReminder
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.SyncSessionEntity
import com.alabadla.internetcardledger.ui.viewmodel.BackupUiState
import com.alabadla.internetcardledger.ui.viewmodel.BackupViewModel
import com.alabadla.internetcardledger.ui.viewmodel.SettingsViewModel
import java.util.Date

private enum class ProtectedAction { EDIT_IDENTITY, REMOVE_DEVICE }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    backupViewModel: BackupViewModel,
    onBackup: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val devices by viewModel.approvedDevices.collectAsStateWithLifecycle()
    val sessions by viewModel.auditSessions.collectAsStateWithLifecycle()
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pinActionName by rememberSaveable { mutableStateOf<String?>(null) }
    val pinAction = pinActionName?.let(ProtectedAction::valueOf)
    var deviceToRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    var showIdentityEditor by rememberSaveable { mutableStateOf(false) }
    var showChangePin by rememberSaveable { mutableStateOf(false) }
    var recoveryRequested by rememberSaveable { mutableStateOf(false) }
    var showRecoveryPin by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val recoveryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) recoveryRequested = false else backupViewModel.export(context.contentResolver, uri)
    }

    LaunchedEffect(backupState, recoveryRequested) {
        if (recoveryRequested && backupState is BackupUiState.Success) {
            recoveryRequested = false
            showRecoveryPin = true
            backupViewModel.dismissMessage()
        }
    }

    Scaffold(topBar = { SecondaryTopBar(stringResource(R.string.action_settings), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // General Device Info
            item {
                state.settings?.identity?.let { identity ->
                    SettingsCard(stringResource(R.string.settings_this_device)) {
                        Text(identity.deviceName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(if (identity.role == DeviceRole.MAIN) R.string.settings_main_role else R.string.role_secondary),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            // Backup Reminders
            item {
                SettingsCard(stringResource(R.string.settings_backup_reminder)) {
                    ReminderButtons(state.settings?.backupReminder ?: BackupReminder.WEEKLY, viewModel::updateReminder)
                    OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.settings_open_backup))
                    }
                }
            }

            // Security PIN Settings
            item {
                SettingsCard(stringResource(R.string.settings_security)) {
                    Button(onClick = { showChangePin = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.settings_change_pin))
                    }
                    OutlinedButton(
                        onClick = { recoveryRequested = true; recoveryLauncher.launch("recovery-backup.json") },
                        enabled = backupState !is BackupUiState.Working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.settings_forgot_backup))
                    }
                    if (recoveryRequested) Text(stringResource(R.string.settings_recovery_required), style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Advanced Settings Section Toggle Button
            item {
                OutlinedButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(
                        text = if (showAdvanced) "إخفاء الإعدادات المتقدمة" else stringResource(R.string.action_advanced_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (showAdvanced) {
                // Advanced Item 1: Device Role / Name edit
                item {
                    SettingsCard("تعديل هوية الهاتف") {
                        Button(
                            onClick = { pinActionName = ProtectedAction.EDIT_IDENTITY.name },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(R.string.settings_edit_identity))
                        }
                    }
                }

                // Advanced Item 2: Approved Devices
                item { Text(stringResource(R.string.settings_approved_devices), style = MaterialTheme.typography.titleLarge) }
                if (devices.isEmpty()) {
                    item { Text(stringResource(R.string.settings_no_approved_devices), style = MaterialTheme.typography.bodyLarge) }
                }
                items(devices, key = { it.deviceId }) { device ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(device.deviceName, style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = { deviceToRemoveId = device.deviceId; pinActionName = ProtectedAction.REMOVE_DEVICE.name },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) { Text(stringResource(R.string.settings_remove_device)) }
                        }
                    }
                }

                // Advanced Item 3: Sync Audit Logs
                item { Text(stringResource(R.string.settings_audit_log), style = MaterialTheme.typography.titleLarge) }
                if (sessions.isEmpty()) {
                    item { Text(stringResource(R.string.settings_no_audit), style = MaterialTheme.typography.bodyLarge) }
                }
                items(sessions.take(10), key = { it.id }) { SessionRow(it) }
            }

            // App Version
            item {
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
                Text(
                    text = stringResource(R.string.settings_version, version),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
    }

    pinAction?.let { action ->
        PinDialog(
            title = stringResource(R.string.pin_required_action),
            onDismiss = { pinActionName = null; deviceToRemoveId = null },
            onForgotPin = { pinActionName = null; recoveryRequested = true; recoveryLauncher.launch("recovery-backup.json") },
            onConfirm = { pin ->
                viewModel.verifyPin(pin) { valid ->
                    if (valid) when (action) {
                        ProtectedAction.EDIT_IDENTITY -> showIdentityEditor = true
                        ProtectedAction.REMOVE_DEVICE -> deviceToRemoveId?.let(viewModel::removeApprovedDevice)
                    }
                    if (valid) { pinActionName = null; deviceToRemoveId = null }
                }
            },
        )
    }
    if (showIdentityEditor) IdentityEditor(
        initialName = state.settings?.identity?.deviceName.orEmpty(),
        initialRole = state.settings?.identity?.role ?: DeviceRole.SECONDARY,
        onDismiss = { showIdentityEditor = false },
        onSave = { name, role -> viewModel.updateIdentity(name, role); showIdentityEditor = false },
    )
    if (showChangePin) ChangePinDialog(
        recovery = false,
        onDismiss = { showChangePin = false },
        onSave = { current, new, confirm -> viewModel.changePin(current, new, confirm); showChangePin = false },
    )
    if (showRecoveryPin) ChangePinDialog(
        recovery = true,
        onDismiss = { showRecoveryPin = false },
        onSave = { _, new, confirm -> viewModel.resetPinAfterBackup(new, confirm); showRecoveryPin = false },
    )
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { Button(onClick = viewModel::clearMessage) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (recoveryRequested && backupState is BackupUiState.Error) {
        AlertDialog(
            onDismissRequest = { recoveryRequested = false; backupViewModel.dismissMessage() },
            title = { Text(stringResource(R.string.backup_replace_warning_title)) },
            text = { Text((backupState as BackupUiState.Error).message, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { recoveryRequested = false; backupViewModel.dismissMessage() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ReminderButtons(selected: BackupReminder, onSelect: (BackupReminder) -> Unit) {
    val values = listOf(
        BackupReminder.DAILY to stringResource(R.string.reminder_daily),
        BackupReminder.WEEKLY to stringResource(R.string.reminder_weekly),
        BackupReminder.NEVER to stringResource(R.string.reminder_never),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { (value, label) ->
            val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            if (selected == value) Button(onClick = { onSelect(value) }, modifier = modifier) { Text(label) }
            else OutlinedButton(onClick = { onSelect(value) }, modifier = modifier) { Text(label) }
        }
    }
}

@Composable
private fun SessionRow(session: SyncSessionEntity) {
    val context = LocalContext.current
    val date = remember(session.startedAt) { DateFormat.getMediumDateFormat(context).format(Date(session.startedAt)) }
    val status = stringResource(
        when (session.status) {
            "SUCCESS" -> R.string.sync_status_success
            "CONFLICT_DETECTED" -> R.string.sync_status_conflict
            else -> R.string.sync_status_failed
        },
    )
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.peerDeviceName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.audit_date_status, date, status), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.audit_counts, session.changesSentCount, session.changesReceivedCount, session.conflictsCount))
        }
    }
}

@Composable
private fun IdentityEditor(initialName: String, initialRole: DeviceRole, onDismiss: () -> Unit, onSave: (String, DeviceRole) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var roleName by rememberSaveable(initialRole) { mutableStateOf(initialRole.name) }
    val role = DeviceRole.valueOf(roleName)
    var confirmMain by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.identity_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text(stringResource(R.string.device_name_short)) }, singleLine = true)
                Button(onClick = { roleName = DeviceRole.SECONDARY.name }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.role_secondary)) }
                OutlinedButton(onClick = { confirmMain = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.role_main)) }
                Text(stringResource(if (role == DeviceRole.MAIN) R.string.role_selected_main_short else R.string.role_selected_secondary_short))
            }
        },
        confirmButton = { Button(onClick = { onSave(name, role) }) { Text(stringResource(R.string.action_save_short)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmMain) AlertDialog(
        onDismissRequest = { confirmMain = false },
        title = { Text(stringResource(R.string.warning_title)) },
        text = { Text(stringResource(R.string.main_only_warning)) },
        confirmButton = { Button(onClick = { roleName = DeviceRole.MAIN.name; confirmMain = false }) { Text(stringResource(R.string.main_confirm_short)) } },
        dismissButton = { TextButton(onClick = { confirmMain = false }) { Text(stringResource(R.string.action_back)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangePinDialog(recovery: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val saveRequester = remember { BringIntoViewRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (recovery) R.string.pin_reset_title else R.string.settings_change_pin)) },
        text = {
            Column(
                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!recovery) PinInput(
                    label = stringResource(R.string.pin_current),
                    value = current,
                    onChange = { current = it },
                )
                PinInput(
                    label = stringResource(R.string.pin_new),
                    value = newPin,
                    onChange = { newPin = it },
                )
                PinInput(
                    label = stringResource(R.string.pin_confirm_new),
                    value = confirmation,
                    onChange = { confirmation = it },
                    followingRequester = saveRequester,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(current, newPin, confirmation) },
                enabled = newPin.length >= 4,
                modifier = Modifier.bringIntoViewRequester(saveRequester),
            ) { Text(stringResource(R.string.action_save_short)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PinInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    followingRequester: BringIntoViewRequester? = null,
) {
    SecurePinField(
        label = label,
        value = value,
        onValueChange = onChange,
        followingRequester = followingRequester,
    )
}
