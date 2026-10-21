package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alabadla.internetcardledger.R

@Composable
fun PinDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onForgotPin: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { value -> pin = value.filter(Char::isDigit).take(8) },
                label = { Text(stringResource(R.string.pin_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, enabled = pin.length >= 4) { Text(stringResource(R.string.pin_continue)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            onForgotPin?.let { forgot -> TextButton(onClick = forgot) { Text(stringResource(R.string.pin_forgot)) } }
        },
    )
}
