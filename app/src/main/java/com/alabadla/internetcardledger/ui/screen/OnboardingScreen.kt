package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.ui.viewmodel.SettingsViewModel

@Composable
fun OnboardingScreen(viewModel: SettingsViewModel) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(DeviceRole.SECONDARY) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirmMain by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("إعداد دفتر كروت الإنترنت", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("هذه الخطوات تظهر مرة واحدة فقط.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(50) },
            label = { Text("اسم هذا الهاتف") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
        Text("اختر دور الهاتف", style = MaterialTheme.typography.titleLarge)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("الهاتف الرئيسي مخصص لهاتف العم فقط. بقية الهواتف يجب أن تكون فرعية.", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { role = DeviceRole.SECONDARY }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("هاتف فرعي") }
                    OutlinedButton(onClick = { confirmMain = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("هاتف رئيسي") }
                }
                Text(if (role == DeviceRole.MAIN) "المحدد: الهاتف الرئيسي" else "المحدد: هاتف فرعي", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text("أنشئ رقمًا سريًا لحماية الإعدادات والاستعادة", style = MaterialTheme.typography.titleLarge)
        PinField("الرقم السري (4 إلى 8 أرقام)", pin) { pin = it }
        PinField("تأكيد الرقم السري", confirmPin) { confirmPin = it }
        Button(
            onClick = { viewModel.completeOnboarding(name, role, pin, confirmPin) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        ) { Text("حفظ وبدء الاستخدام", style = MaterialTheme.typography.titleMedium) }
    }

    if (confirmMain) AlertDialog(
        onDismissRequest = { confirmMain = false },
        title = { Text("تأكيد الهاتف الرئيسي") },
        text = { Text("اختر هذا الدور فقط إذا كان هذا هو هاتف العم الرئيسي. هل أنت متأكد؟", style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = { role = DeviceRole.MAIN; confirmMain = false }) { Text("نعم، هذا هاتف العم") } },
        dismissButton = { TextButton(onClick = { confirmMain = false }) { Text("رجوع") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(8)) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    )
}
