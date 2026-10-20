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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.ui.viewmodel.BackupPreview
import com.alabadla.internetcardledger.ui.viewmodel.BackupUiState
import com.alabadla.internetcardledger.ui.viewmodel.BackupViewModel
import java.util.Date

@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showReplaceWarning by remember { mutableStateOf(false) }
    var showFinalReplaceConfirmation by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspect(context.contentResolver, uri)
    }

    Scaffold(topBar = { SecondaryTopBar(title = stringResource(R.string.backup_title), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.backup_explanation), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { exportLauncher.launch("internet-card-ledger-backup.json") },
                enabled = state !is BackupUiState.Working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.backup_export), style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                enabled = state !is BackupUiState.Working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.backup_choose_restore), style = MaterialTheme.typography.titleMedium) }

            when (val current = state) {
                BackupUiState.Idle -> Unit
                is BackupUiState.Working -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(current.message, style = MaterialTheme.typography.bodyLarge)
                }
                is BackupUiState.Ready -> RestoreSummary(
                    preview = current.preview,
                    onMerge = viewModel::merge,
                    onReplace = { showReplaceWarning = true },
                )
                is BackupUiState.Success -> StatusCard(current.message, isError = false, viewModel::dismissMessage)
                is BackupUiState.Error -> StatusCard(current.message, isError = true, viewModel::dismissMessage)
            }
        }
    }

    if (showReplaceWarning) {
        AlertDialog(
            onDismissRequest = { showReplaceWarning = false },
            title = { Text(stringResource(R.string.backup_replace_warning_title)) },
            text = { Text(stringResource(R.string.backup_replace_warning), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { showReplaceWarning = false; showFinalReplaceConfirmation = true }) {
                    Text(stringResource(R.string.backup_continue))
                }
            },
            dismissButton = { TextButton(onClick = { showReplaceWarning = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showFinalReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = { showFinalReplaceConfirmation = false },
            title = { Text(stringResource(R.string.backup_final_confirmation_title)) },
            text = { Text(stringResource(R.string.backup_final_confirmation), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { showFinalReplaceConfirmation = false; viewModel.replace() }) {
                    Text(stringResource(R.string.backup_replace_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { showFinalReplaceConfirmation = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RestoreSummary(preview: BackupPreview, onMerge: () -> Unit, onReplace: () -> Unit) {
    val context = LocalContext.current
    val date = remember(preview.createdAt) {
        DateFormat.getMediumDateFormat(context).format(Date(preview.createdAt)) + " " +
            DateFormat.getTimeFormat(context).format(Date(preview.createdAt))
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_valid_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.backup_date_value, date), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.backup_records_value, preview.recordCount), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.backup_record_breakdown, preview.customerCount, preview.saleCount, preview.paymentCount),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.backup_merge_explanation), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onMerge, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                Text(stringResource(R.string.backup_merge), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onReplace, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                Text(stringResource(R.string.backup_replace), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_close), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
