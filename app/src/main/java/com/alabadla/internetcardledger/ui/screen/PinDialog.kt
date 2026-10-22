package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alabadla.internetcardledger.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onForgotPin: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    val continueRequester = remember { BringIntoViewRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())) {
                SecurePinField(
                    label = stringResource(R.string.pin_label),
                    value = pin,
                    onValueChange = { pin = it },
                    followingRequester = continueRequester,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = pin.length >= 4,
                modifier = Modifier.bringIntoViewRequester(continueRequester),
            ) { Text(stringResource(R.string.pin_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            onForgotPin?.let { forgot -> TextButton(onClick = forgot) { Text(stringResource(R.string.pin_forgot)) } }
        },
    )
}
