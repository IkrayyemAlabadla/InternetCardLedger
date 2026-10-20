package com.alabadla.internetcardledger.ui.screen

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.data.repository.BackupReminder
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
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
    var pinAction by remember { mutableStateOf<ProtectedAction?>(null) }
    var deviceToRemove by remember { mutableStateOf<ApprovedDeviceEntity?>(null) }
    var showIdentityEditor by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var recoveryRequested by remember { mutableStateOf(false) }
    var showRecoveryPin by remember { mutableStateOf(false) }
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

    Scaffold(topBar = { SecondaryTopBar("الإعدادات", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                state.settings?.identity?.let { identity ->
                    SettingsCard("هذا الجهاز") {
                        Text(identity.deviceName, style = MaterialTheme.typography.titleLarge)
                        Text(if (identity.role == DeviceRole.MAIN) "الهاتف الرئيسي — هاتف العم" else "هاتف فرعي", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { pinAction = ProtectedAction.EDIT_IDENTITY }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("تعديل الاسم أو الدور")
                        }
                    }
                }
            }
            item {
                SettingsCard("تذكير النسخ الاحتياطي") {
                    ReminderButtons(state.settings?.backupReminder ?: BackupReminder.WEEKLY, viewModel::updateReminder)
                    OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("فتح النسخ الاحتياطي والاستعادة")
                    }
                }
            }
            item { Text("الأجهزة المعتمدة", style = MaterialTheme.typography.titleLarge) }
            if (devices.isEmpty()) item { Text("لا توجد أجهزة معتمدة حاليًا.", style = MaterialTheme.typography.bodyLarge) }
            items(devices, key = { it.deviceId }) { device ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(device.deviceName, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { deviceToRemove = device; pinAction = ProtectedAction.REMOVE_DEVICE },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("إزالة اعتماد الجهاز") }
                    }
                }
            }
            item {
                SettingsCard("الأمان") {
                    Button(onClick = { showChangePin = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("تغيير الرقم السري") }
                    OutlinedButton(
                        onClick = { recoveryRequested = true; recoveryLauncher.launch("recovery-backup.json") },
                        enabled = backupState !is BackupUiState.Working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("نسيت الرقم السري — إنشاء نسخة استرداد") }
                    if (recoveryRequested) Text("يجب حفظ نسخة الاسترداد بنجاح قبل تعيين رقم جديد.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { Text("سجل المزامنة", style = MaterialTheme.typography.titleLarge) }
            if (sessions.isEmpty()) item { Text("لا توجد عمليات مزامنة مسجلة.", style = MaterialTheme.typography.bodyLarge) }
            items(sessions.take(10), key = { it.id }) { SessionRow(it) }
            item {
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0" }
                Text("إصدار التطبيق: $version", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
    }

    pinAction?.let { action ->
        PinDialog(
            title = "أدخل الرقم السري للمتابعة",
            onDismiss = { pinAction = null; deviceToRemove = null },
            onForgotPin = { pinAction = null; recoveryRequested = true; recoveryLauncher.launch("recovery-backup.json") },
            onConfirm = { pin ->
                viewModel.verifyPin(pin) { valid ->
                    if (valid) when (action) {
                        ProtectedAction.EDIT_IDENTITY -> showIdentityEditor = true
                        ProtectedAction.REMOVE_DEVICE -> deviceToRemove?.let { viewModel.removeApprovedDevice(it.deviceId) }
                    }
                    if (valid) { pinAction = null; deviceToRemove = null }
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
            confirmButton = { Button(onClick = viewModel::clearMessage) { Text("حسنًا") } },
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
    val values = listOf(BackupReminder.DAILY to "يوميًا", BackupReminder.WEEKLY to "أسبوعيًا", BackupReminder.NEVER to "بدون تذكير")
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
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.peerDeviceName, style = MaterialTheme.typography.titleMedium)
            Text("$date — ${session.status}", style = MaterialTheme.typography.bodyLarge)
            Text("مرسلة: ${session.changesSentCount}، مستلمة: ${session.changesReceivedCount}، تعارضات: ${session.conflictsCount}")
        }
    }
}

@Composable
private fun IdentityEditor(initialName: String, initialRole: DeviceRole, onDismiss: () -> Unit, onSave: (String, DeviceRole) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var role by remember { mutableStateOf(initialRole) }
    var confirmMain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات الجهاز") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text("اسم الجهاز") }, singleLine = true)
                Button(onClick = { role = DeviceRole.SECONDARY }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("هاتف فرعي") }
                OutlinedButton(onClick = { confirmMain = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("هاتف رئيسي") }
                Text(if (role == DeviceRole.MAIN) "المحدد: رئيسي" else "المحدد: فرعي")
            }
        },
        confirmButton = { Button(onClick = { onSave(name, role) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmMain) AlertDialog(
        onDismissRequest = { confirmMain = false },
        title = { Text("تحذير") },
        text = { Text("يجب أن يكون هاتف العم وحده هو الهاتف الرئيسي.") },
        confirmButton = { Button(onClick = { role = DeviceRole.MAIN; confirmMain = false }) { Text("تأكيد الهاتف الرئيسي") } },
        dismissButton = { TextButton(onClick = { confirmMain = false }) { Text("رجوع") } },
    )
}

@Composable
private fun ChangePinDialog(recovery: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recovery) "تعيين رقم سري جديد" else "تغيير الرقم السري") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!recovery) PinInput("الرقم السري الحالي", current) { current = it }
                PinInput("الرقم السري الجديد", newPin) { newPin = it }
                PinInput("تأكيد الرقم الجديد", confirmation) { confirmation = it }
            }
        },
        confirmButton = { Button(onClick = { onSave(current, newPin, confirmation) }, enabled = newPin.length >= 4) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PinInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(8)) }, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}
