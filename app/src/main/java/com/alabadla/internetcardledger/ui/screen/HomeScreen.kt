package com.alabadla.internetcardledger.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.domain.DashboardSummary
import com.alabadla.internetcardledger.ui.viewmodel.DashboardUiState

@Composable
fun HomeScreen(
    dashboardState: DashboardUiState,
    onNewSale: () -> Unit,
    onCustomers: () -> Unit,
    onHistory: () -> Unit,
    onBackup: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                when (dashboardState) {
                    DashboardUiState.Loading -> DashboardLoading()
                    DashboardUiState.Error -> DashboardError()
                    is DashboardUiState.Content -> {
                        DashboardSummaryCards(summary = dashboardState.overview.summary)
                        Text(
                            text = stringResource(R.string.recent_activity),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (dashboardState.overview.recentHistory.isEmpty()) {
                            Text(
                                text = stringResource(R.string.history_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            dashboardState.overview.recentHistory.forEach { record ->
                                HistoryRecordCard(record = record, onClick = onHistory)
                            }
                        }
                    }
                }

                FeatureButton(
                    text = stringResource(R.string.action_add_sale),
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.icon_add_sale_description),
                    onClick = onNewSale,
                )
                FeatureButton(
                    text = stringResource(R.string.action_customers),
                    icon = Icons.Default.Person,
                    contentDescription = stringResource(R.string.icon_customers_description),
                    onClick = onCustomers,
                )
                FeatureButton(
                    text = stringResource(R.string.action_history),
                    icon = Icons.Default.Search,
                    contentDescription = stringResource(R.string.action_history),
                    onClick = onHistory,
                )
                FeatureButton(
                    text = stringResource(R.string.action_sync),
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.icon_sync_description),
                    onClick = onSync,
                )
                FeatureButton(
                    text = stringResource(R.string.backup_title),
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.backup_title),
                    onClick = onBackup,
                )
                FeatureButton(
                    text = stringResource(R.string.action_settings),
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.icon_settings_description),
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun DashboardLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.dashboard_loading), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DashboardError() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun DashboardSummaryCards(summary: DashboardSummary) {
    SummaryCard(
        label = stringResource(R.string.dashboard_total_sales),
        value = stringResource(R.string.money_minor_value, summary.totalSalesMinor),
    )
    SummaryCard(
        label = stringResource(R.string.dashboard_total_payments),
        value = stringResource(R.string.money_minor_value, summary.totalPaymentsMinor),
    )
    SummaryCard(
        label = stringResource(R.string.dashboard_balance),
        value = stringResource(R.string.money_minor_value, summary.outstandingBalanceMinor),
        highlight = summary.outstandingBalanceMinor > 0L,
    )
    SummaryCard(
        label = stringResource(R.string.dashboard_quantity),
        value = summary.totalQuantity.toString(),
    )
    SummaryCard(
        label = stringResource(R.string.dashboard_counts),
        value = stringResource(
            R.string.dashboard_counts_value,
            summary.activeCustomers,
            summary.activeSales,
            summary.activePayments,
        ),
    )
    if (summary.pendingChanges > 0) {
        SummaryCard(
            label = stringResource(R.string.pending_sync),
            value = summary.pendingChanges.toString(),
            highlight = true,
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, highlight: Boolean = false) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FeatureButton(
    text: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(end = 14.dp),
        )
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
