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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.ui.util.CurrencyFormatter
import com.alabadla.internetcardledger.ui.viewmodel.SaleFormError
import com.alabadla.internetcardledger.ui.viewmodel.SaleFormState
import com.alabadla.internetcardledger.ui.viewmodel.SalesViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class SalesPane { LIST, DETAILS, FORM }

@Composable
fun SalesScreen(
    viewModel: SalesViewModel,
    onBack: () -> Unit,
    initialSaleId: String? = null,
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedSale by viewModel.selectedSale.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deletedSaleId by viewModel.deletedSaleId.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    var paneName by rememberSaveable { mutableStateOf(SalesPane.LIST.name) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val pane = SalesPane.valueOf(paneName)

    LaunchedEffect(initialSaleId) {
        if (initialSaleId != null) {
            viewModel.selectSale(initialSaleId)
            paneName = SalesPane.DETAILS.name
        }
    }

    fun returnToList() {
        paneName = SalesPane.LIST.name
        showDeleteDialog = false
    }

    LaunchedEffect(formState.savedSaleId) {
        if (formState.savedSaleId != null) {
            paneName = SalesPane.DETAILS.name
            viewModel.clearSaveResult()
        }
    }
    LaunchedEffect(deletedSaleId) {
        if (deletedSaleId != null) {
            returnToList()
            viewModel.clearDeleteResult()
        }
    }

    val handleBack: () -> Unit = when (pane) {
        SalesPane.LIST -> onBack
        SalesPane.DETAILS -> ({ returnToList() })
        SalesPane.FORM -> {
            {
                paneName = if (formState.editingSale == null) {
                    SalesPane.LIST.name
                } else {
                    SalesPane.DETAILS.name
                }
            }
        }
    }
    BackHandler(onBack = handleBack)

    when (pane) {
        SalesPane.LIST -> SalesList(
            sales = sales,
            query = query,
            onQueryChanged = viewModel::updateSearchQuery,
            onAdd = {
                viewModel.beginCreate()
                paneName = SalesPane.FORM.name
            },
            onOpen = { sale ->
                viewModel.selectSale(sale.id)
                paneName = SalesPane.DETAILS.name
            },
            onBack = onBack,
        )

        SalesPane.DETAILS -> SaleDetails(
            sale = selectedSale,
            isDeleting = isDeleting,
            onEdit = {
                selectedSale?.let(viewModel::beginEdit)
                paneName = SalesPane.FORM.name
            },
            onDelete = { showDeleteDialog = true },
            onBack = { returnToList() },
        )

        SalesPane.FORM -> SaleForm(
            state = formState,
            customers = customers,
            onCustomerIdChanged = viewModel::updateCustomerId,
            onDescriptionChanged = viewModel::updateDescription,
            onQuantityChanged = viewModel::updateQuantity,
            onPriceChanged = viewModel::updateUnitPrice,
            onNotesChanged = viewModel::updateNotes,
            onSave = viewModel::saveSale,
            onBack = handleBack,
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_question)) },
            text = { Text(stringResource(R.string.delete_sale_message)) },
            confirmButton = {
                Button(
                    onClick = viewModel::deleteSelectedSale,
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

@Composable
private fun SalesList(
    sales: List<SaleEntity>,
    query: String,
    onQueryChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (SaleEntity) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { SecondaryTopBar(stringResource(R.string.sales_title), onBack) },
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
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_sales)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_sales))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.icon_add_sale_description))
                Text(
                    text = stringResource(R.string.action_add_sale),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (sales.isEmpty()) {
                Text(
                    text = stringResource(
                        if (query.isBlank()) R.string.sales_empty else R.string.sales_search_empty,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(sales, key = SaleEntity::id) { sale ->
                        SaleCard(sale = sale, onClick = { onOpen(sale) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleCard(sale: SaleEntity, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(sale.description, style = MaterialTheme.typography.titleLarge)
            val priceStr = CurrencyFormatter.formatMoney(sale.unitPriceMinor)
            val totalStr = CurrencyFormatter.formatMoney(sale.quantity.toLong() * sale.unitPriceMinor)
            Text(
                "الكمية: ${sale.quantity} — السعر: $priceStr (الإجمالي: $totalStr)",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SaleDetails(
    sale: SaleEntity?,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { SecondaryTopBar(stringResource(R.string.sale_details_title), onBack) },
    ) { innerPadding ->
        if (sale == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                DetailRow(R.string.sale_description, sale.description)
                DetailRow(R.string.sale_quantity, CurrencyFormatter.formatQuantity(sale.quantity))
                DetailRow(R.string.sale_unit_price, CurrencyFormatter.formatMoney(sale.unitPriceMinor))
                DetailRow(
                    R.string.dashboard_total_sales,
                    CurrencyFormatter.formatMoney(sale.quantity.toLong() * sale.unitPriceMinor),
                )
                DetailRow(R.string.sale_date, formatDate(sale.soldAt))
                DetailRow(R.string.sale_notes, sale.notes ?: stringResource(R.string.no_notes))

                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    Text(stringResource(R.string.action_edit), modifier = Modifier.padding(start = 12.dp))
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
                    Text(stringResource(R.string.action_delete), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(labelResource: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(labelResource), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SaleForm(
    state: SaleFormState,
    customers: List<CustomerEntity>,
    onCustomerIdChanged: (String?) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val errorText = state.error?.let { stringResource(it.stringResource()) }
    Scaffold(
        topBar = {
            SecondaryTopBar(
                title = stringResource(
                    if (state.editingSale == null) R.string.add_sale_title else R.string.edit_sale_title,
                ),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Section 1: Customer Link
            if (customers.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val selectedCustomerName = customers.find { it.id == state.customerId }?.name
                            ?: stringResource(R.string.no_customer_selected)
                        Text(
                            text = "${stringResource(R.string.customer_select_label)}: $selectedCustomerName",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onCustomerIdChanged(null) },
                                enabled = !state.isSaving && state.customerId != null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) { Text(stringResource(R.string.no_customer_selected)) }
                            customers.forEach { customer ->
                                val selected = state.customerId == customer.id
                                if (selected) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    ) { Text(customer.name) }
                                } else {
                                    OutlinedButton(
                                        onClick = { onCustomerIdChanged(customer.id) },
                                        enabled = !state.isSaving,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    ) { Text(customer.name) }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Details & Price
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "تفاصيل البيع والسعر",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sale_description_required)) },
                        placeholder = { Text("مثال: كرت نت 50 جيجا") },
                        enabled = !state.isSaving,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = state.quantity,
                        onValueChange = onQuantityChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sale_quantity_required)) },
                        placeholder = { Text("مثال: 1") },
                        enabled = !state.isSaving,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = state.unitPriceMinor,
                        onValueChange = onPriceChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sale_unit_price_required)) },
                        placeholder = { Text("مثال: 50") },
                        trailingIcon = {
                            Text(
                                text = stringResource(R.string.currency_shekel),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        enabled = !state.isSaving,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = onNotesChanged,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text(stringResource(R.string.sale_notes)) },
                        enabled = !state.isSaving,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 2,
                    )
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun SaleFormError.stringResource(): Int = when (this) {
    SaleFormError.DESCRIPTION_REQUIRED -> R.string.error_description_required
    SaleFormError.QUANTITY_REQUIRED -> R.string.error_quantity_required
    SaleFormError.QUANTITY_INVALID -> R.string.error_quantity_invalid
    SaleFormError.PRICE_REQUIRED -> R.string.error_price_required
    SaleFormError.PRICE_INVALID -> R.string.error_price_invalid
    SaleFormError.SAVE_FAILED -> R.string.error_save_failed
}

private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
    Locale.forLanguageTag("ar"),
).format(Date(timestamp))
