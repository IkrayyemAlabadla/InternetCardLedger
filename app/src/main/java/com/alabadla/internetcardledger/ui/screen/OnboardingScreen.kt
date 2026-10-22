package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var roleName by rememberSaveable { mutableStateOf(DeviceRole.SECONDARY.name) }
    val role = DeviceRole.valueOf(roleName)
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirmMain by rememberSaveable { mutableStateOf(false) }
    val finishRequester = remember { BringIntoViewRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.onboarding_once), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(50) },
            label = { Text(stringResource(R.string.device_name_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
        Text(stringResource(R.string.device_role_choose), style = MaterialTheme.typography.titleLarge)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.main_device_warning), style = MaterialTheme.typography.bodyLarge)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { roleName = DeviceRole.SECONDARY.name }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.role_secondary)) }
                    OutlinedButton(onClick = { confirmMain = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.role_main)) }
                }
                Text(stringResource(if (role == DeviceRole.MAIN) R.string.role_selected_main else R.string.role_selected_secondary), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(stringResource(R.string.pin_create_explanation), style = MaterialTheme.typography.titleLarge)
        SecurePinField(stringResource(R.string.pin_new_label), pin, onValueChange = { pin = it })
        SecurePinField(
            stringResource(R.string.pin_confirm_label),
            confirmPin,
            onValueChange = { confirmPin = it },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = { viewModel.completeOnboarding(name, role, pin, confirmPin) },
            ),
            followingRequester = finishRequester,
        )
        Button(
            onClick = { viewModel.completeOnboarding(name, role, pin, confirmPin) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .bringIntoViewRequester(finishRequester),
        ) { Text(stringResource(R.string.onboarding_finish), style = MaterialTheme.typography.titleMedium) }
    }

    if (confirmMain) AlertDialog(
        onDismissRequest = { confirmMain = false },
        title = { Text(stringResource(R.string.main_confirm_title)) },
        text = { Text(stringResource(R.string.main_confirm_message), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = { roleName = DeviceRole.MAIN.name; confirmMain = false }) { Text(stringResource(R.string.main_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirmMain = false }) { Text(stringResource(R.string.action_back)) } },
    )
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { Button(onClick = viewModel::clearMessage) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
