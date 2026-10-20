package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.data.sync.SyncConflictEntity
import com.alabadla.internetcardledger.data.sync.SyncJsonCodec

@Composable
fun ConflictReviewScreen(
    conflicts: List<SyncConflictEntity>,
    onAcceptMain: (conflictId: String) -> Unit,
    onAcceptSecondary: (conflictId: String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SecondaryTopBar(
                title = stringResource(R.string.title_conflict_review),
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
            if (conflicts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.conflicts_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(conflicts, key = { it.id }) { conflict ->
                        ConflictCard(
                            conflict = conflict,
                            onAcceptMain = { onAcceptMain(conflict.id) },
                            onAcceptSecondary = { onAcceptSecondary(conflict.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflictEntity,
    onAcceptMain: () -> Unit,
    onAcceptSecondary: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val entityTypeName = when (conflict.entityType) {
                "CUSTOMER" -> stringResource(R.string.entity_type_customer)
                "SALE" -> stringResource(R.string.entity_type_sale)
                "PAYMENT" -> stringResource(R.string.entity_type_payment)
                else -> conflict.entityType
            }

            Text(
                text = "${stringResource(R.string.conflict_detected_in)}: $entityTypeName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )

            // Main Version details
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.version_main_device),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatVersionPreview(conflict.entityType, conflict.mainVersionJson),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Secondary Version details
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.version_secondary_device),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = formatVersionPreview(conflict.entityType, conflict.secondaryVersionJson),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAcceptMain,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_accept_main))
                }
                OutlinedButton(
                    onClick = onAcceptSecondary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_accept_secondary))
                }
            }
        }
    }
}

private fun formatVersionPreview(entityType: String, jsonStr: String): String {
    return runCatching {
        when (entityType) {
            "CUSTOMER" -> {
                val c = SyncJsonCodec.customerFromJson(jsonStr)
                "الاسم: ${c.name} | الهاتف: ${c.phone ?: "لا يوجد"}"
            }
            "SALE" -> {
                val s = SyncJsonCodec.saleFromJson(jsonStr)
                "الوصف: ${s.description} | الكمية: ${s.quantity} | السعر: ${s.unitPriceMinor}"
            }
            "PAYMENT" -> {
                val p = SyncJsonCodec.paymentFromJson(jsonStr)
                "المبلغ: ${p.amountMinor}"
            }
            else -> jsonStr
        }
    }.getOrDefault(jsonStr)
}
