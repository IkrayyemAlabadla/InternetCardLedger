package com.alabadla.internetcardledger.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.ui.util.CurrencyFormatter
import com.alabadla.internetcardledger.ui.viewmodel.CustomerDetailsState
import com.alabadla.internetcardledger.ui.viewmodel.CustomerFormError
import com.alabadla.internetcardledger.ui.viewmodel.CustomerFormState
import com.alabadla.internetcardledger.ui.viewmodel.CustomerSummary
import com.alabadla.internetcardledger.ui.viewmodel.CustomersViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CustomersPane {
    LIST,
    DETAILS,
    FORM,
}

@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel,
    onBack: () -> Unit,
    initialCustomerId: String? = null,
) {
    val customers by viewModel.customerSummaries.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedDetails by viewModel.selectedCustomerDetails.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deletedCustomerId by viewModel.deletedCustomerId.collectAsStateWithLifecycle()

    var paneName by rememberSaveable { mutableStateOf(CustomersPane.LIST.name) }
    val currentPane = runCatching { CustomersPane.valueOf(paneName) }.getOrDefault(CustomersPane.LIST)

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialCustomerId) {
        if (initialCustomerId != null) {
            viewModel.selectCustomer(initialCustomerId)
            paneName = CustomersPane.DETAILS.name
        }
    }

    val returnToList = {
        paneName = CustomersPane.LIST.name
        viewModel.selectCustomer(null)
    }

    LaunchedEffect(formState.savedCustomerId) {
        if (formState.savedCustomerId != null) {
            paneName = CustomersPane.DETAILS.name
            viewModel.clearSaveResult()
        }
    }

    LaunchedEffect(deletedCustomerId) {
        if (deletedCustomerId != null) {
            returnToList()
            viewModel.clearDeleteResult()
            showDeleteDialog = false
        }
    }

    val handleBack = {
        when (currentPane) {
            CustomersPane.LIST -> onBack()
            CustomersPane.DETAILS, CustomersPane.FORM -> returnToList()
        }
    }

    BackHandler(onBack = handleBack)

    when (currentPane) {
        CustomersPane.LIST -> {
            CustomersList(
                customers = customers,
                query = query,
                onQueryChange = viewModel::updateSearchQuery,
                onOpen = { summary ->
                    viewModel.selectCustomer(summary.customer.id)
                    paneName = CustomersPane.DETAILS.name
                },
                onCreate = {
                    viewModel.beginCreate()
                    paneName = CustomersPane.FORM.name
                },
                onBack = handleBack,
            )
        }

        CustomersPane.DETAILS -> {
            CustomerDetails(
                details = selectedDetails,
                isDeleting = isDeleting,
                onEdit = { customer ->
                    viewModel.beginEdit(customer)
                    paneName = CustomersPane.FORM.name
                },
                onDelete = { showDeleteDialog = true },
                onBack = handleBack,
            )

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                    title = { Text(stringResource(R.string.delete_confirm_question)) },
                    text = { Text(stringResource(R.string.delete_customer_message)) },
                    confirmButton = {
                        Button(
                            onClick = viewModel::deleteSelectedCustomer,
                            enabled = !isDeleting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(R.string.delete_confirm_action))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showDeleteDialog = false },
                            enabled = !isDeleting,
                        ) {
                            Text(stringResource(R.string.action_back))
                        }
                    },
                )
            }
        }

        CustomersPane.FORM -> {
            CustomerForm(
                state = formState,
                onNameChange = viewModel::updateName,
                onPhoneChange = viewModel::updatePhone,
                onNotesChange = viewModel::updateNotes,
                onSave = viewModel::saveCustomer,
                onBack = handleBack,
            )
        }
    }
}

@Composable
private fun CustomersList(
    customers: List<CustomerSummary>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (CustomerSummary) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { SecondaryTopBar(stringResource(R.string.action_customers), onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_customers)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_customers),
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
            )

            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_customer),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.action_add_customer),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (customers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            if (query.isBlank()) R.string.customers_empty else R.string.customers_search_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(customers, key = { it.customer.id }) { summary ->
                        CustomerCard(summary = summary, onClick = { onOpen(summary) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(summary: CustomerSummary, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = summary.customer.name, style = MaterialTheme.typography.titleLarge)
            summary.customer.phone?.takeIf(String::isNotBlank)?.let { phone ->
                Text(
                    text = stringResource(R.string.customer_phone, phone),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            summary.customer.notes?.takeIf(String::isNotBlank)?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${stringResource(R.string.customer_total_sales)}: ${CurrencyFormatter.formatMoney(summary.totalSalesMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${stringResource(R.string.customer_total_payments)}: ${CurrencyFormatter.formatMoney(summary.totalPaymentsMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${stringResource(R.string.customer_balance)}: ${CurrencyFormatter.formatMoney(summary.balanceMinor)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (summary.balanceMinor > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CustomerDetails(
    details: CustomerDetailsState?,
    isDeleting: Boolean,
    onEdit: (CustomerEntity) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { SecondaryTopBar(stringResource(R.string.customer_details_title), onBack) },
    ) { innerPadding ->
        if (details == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.loading),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            val customer = details.customer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = customer.name, style = MaterialTheme.typography.titleLarge)
                        customer.phone?.takeIf(String::isNotBlank)?.let { phone ->
                            Text(
                                text = stringResource(R.string.customer_phone, phone),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        customer.notes?.takeIf(String::isNotBlank)?.let { notes ->
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DetailRow(R.string.customer_total_sales, CurrencyFormatter.formatMoney(details.totalSalesMinor))
                        DetailRow(R.string.customer_total_payments, CurrencyFormatter.formatMoney(details.totalPaymentsMinor))
                        DetailRow(R.string.customer_balance, CurrencyFormatter.formatMoney(details.balanceMinor))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onEdit(customer) },
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        Text(stringResource(R.string.action_edit), modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = onDelete,
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        Text(stringResource(R.string.action_delete), modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Text(
                    text = stringResource(R.string.customer_sales_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (details.sales.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_sales_for_customer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    details.sales.forEach { sale ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = sale.description, style = MaterialTheme.typography.titleMedium)
                                val unitPriceStr = CurrencyFormatter.formatMoney(sale.unitPriceMinor)
                                Text(
                                    text = "الكمية: ${sale.quantity} — سعر الوحدة: $unitPriceStr",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = formatDate(sale.soldAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.customer_payments_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (details.payments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_payments_for_customer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    details.payments.forEach { payment ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = CurrencyFormatter.formatMoney(payment.amountMinor),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = formatDate(payment.paidAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(labelResource: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(labelResource), style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CustomerForm(
    state: CustomerFormState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SecondaryTopBar(
                stringResource(
                    if (state.editingCustomer == null) R.string.add_customer_title else R.string.edit_customer_title,
                ),
                onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val errorText = when (state.error) {
                CustomerFormError.NAME_REQUIRED -> stringResource(R.string.error_customer_name_required)
                CustomerFormError.SAVE_FAILED -> stringResource(R.string.error_customer_save_failed)
                null -> null
            }

            if (errorText != null) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.customer_name_required)) },
                placeholder = { Text("مثال: أبو محمد") },
                isError = state.error == CustomerFormError.NAME_REQUIRED,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.customer_phone_label)) },
                placeholder = { Text("مثال: 0599123456") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.customer_notes_label)) },
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text(
                        stringResource(R.string.action_save),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))
    return formatter.format(Date(timestamp))
}
