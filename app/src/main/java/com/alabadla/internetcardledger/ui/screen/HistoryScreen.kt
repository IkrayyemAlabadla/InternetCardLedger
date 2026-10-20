package com.alabadla.internetcardledger.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.domain.HistoryDateFilter
import com.alabadla.internetcardledger.domain.HistoryOperation
import com.alabadla.internetcardledger.domain.HistoryRecord
import com.alabadla.internetcardledger.domain.HistoryRecordType
import com.alabadla.internetcardledger.ui.viewmodel.DashboardViewModel
import com.alabadla.internetcardledger.ui.viewmodel.HistoryUiState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenSale: (String) -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    val state by viewModel.historyState.collectAsStateWithLifecycle()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRecord = selectedKey?.let { key ->
        state.records.firstOrNull { record -> record.key == key }
    }

    if (selectedRecord != null) {
        BackHandler { selectedKey = null }
        HistoryDetails(record = selectedRecord, onBack = { selectedKey = null })
    } else {
        HistoryList(
            state = state,
            onQueryChange = viewModel::updateSearchQuery,
            onTypeChange = viewModel::updateRecordType,
            onDateChange = viewModel::updateDateFilter,
            onOpen = { record ->
                when {
                    record.isDeleted -> selectedKey = record.key
                    record.type == HistoryRecordType.SALE -> onOpenSale(record.recordId)
                    record.type == HistoryRecordType.CUSTOMER -> onOpenCustomer(record.recordId)
                    else -> selectedKey = record.key
                }
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onQueryChange: (String) -> Unit,
    onTypeChange: (HistoryRecordType) -> Unit,
    onDateChange: (HistoryDateFilter) -> Unit,
    onOpen: (HistoryRecord) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { SecondaryTopBar(stringResource(R.string.history_title), onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_history)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_history))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.filter_record_type), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryRecordType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(stringResource(type.labelResource())) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text(stringResource(R.string.filter_date), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryDateFilter.entries.forEach { date ->
                    FilterChip(
                        selected = state.dateFilter == date,
                        onClick = { onDateChange(date) },
                        label = { Text(stringResource(date.labelResource())) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            when {
                state.isLoading -> CenteredMessage(
                    loading = true,
                    messageResource = R.string.history_loading,
                )
                state.hasError -> CenteredMessage(
                    loading = false,
                    messageResource = R.string.history_error,
                )
                state.records.isEmpty() -> CenteredMessage(
                    loading = false,
                    messageResource = R.string.history_empty_filtered,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.records, key = { record -> record.key }) { record ->
                        HistoryRecordCard(record = record, onClick = { onOpen(record) })
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRecordCard(record: HistoryRecord, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = record.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${stringResource(record.type.labelResource())} — ${stringResource(record.operation.labelResource())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatHistoryDate(record.updatedAt),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (record.isPending) {
                Text(
                    text = stringResource(R.string.pending_sync),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HistoryDetails(record: HistoryRecord, onBack: () -> Unit) {
    Scaffold(
        topBar = { SecondaryTopBar(stringResource(R.string.history_details_title), onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HistoryDetailRow(R.string.history_record_type, stringResource(record.type.labelResource()))
            HistoryDetailRow(R.string.history_operation, stringResource(record.operation.labelResource()))
            HistoryDetailRow(R.string.history_record_name, record.displayTitle())
            record.relatedName?.let { name ->
                HistoryDetailRow(R.string.history_related_customer, name)
            }
            record.quantity?.let { quantity ->
                HistoryDetailRow(R.string.sale_quantity, quantity.toString())
            }
            record.amountMinor?.let { amount ->
                HistoryDetailRow(R.string.history_amount, stringResource(R.string.money_minor_value, amount))
            }
            HistoryDetailRow(R.string.history_updated_at, formatHistoryDate(record.updatedAt))
            if (record.isPending) {
                Text(
                    text = stringResource(R.string.pending_sync),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailRow(labelResource: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(labelResource), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CenteredMessage(loading: Boolean, messageResource: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) CircularProgressIndicator()
        Text(stringResource(messageResource), style = MaterialTheme.typography.bodyLarge)
    }
}

private val HistoryRecord.key: String
    get() = "${type.name}:$recordId"

@Composable
private fun HistoryRecord.displayTitle(): String = when (type) {
    HistoryRecordType.SALE -> title ?: stringResource(R.string.history_sale_fallback)
    HistoryRecordType.CUSTOMER -> title ?: stringResource(R.string.history_customer_fallback)
    HistoryRecordType.PAYMENT -> relatedName?.let { stringResource(R.string.history_payment_for, it) }
        ?: stringResource(R.string.history_payment_fallback)
    HistoryRecordType.ALL -> stringResource(R.string.history_record_fallback)
}

private fun HistoryRecordType.labelResource(): Int = when (this) {
    HistoryRecordType.ALL -> R.string.filter_all
    HistoryRecordType.SALE -> R.string.filter_sales
    HistoryRecordType.PAYMENT -> R.string.filter_payments
    HistoryRecordType.CUSTOMER -> R.string.filter_customers
}

private fun HistoryDateFilter.labelResource(): Int = when (this) {
    HistoryDateFilter.ALL -> R.string.filter_all_dates
    HistoryDateFilter.TODAY -> R.string.filter_today
    HistoryDateFilter.LAST_7_DAYS -> R.string.filter_last_7_days
    HistoryDateFilter.LAST_30_DAYS -> R.string.filter_last_30_days
}

private fun HistoryOperation.labelResource(): Int = when (this) {
    HistoryOperation.CREATED -> R.string.operation_created
    HistoryOperation.UPDATED -> R.string.operation_updated
    HistoryOperation.DELETED -> R.string.operation_deleted
}

private fun formatHistoryDate(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
    Locale("ar"),
).format(Date(timestamp))
