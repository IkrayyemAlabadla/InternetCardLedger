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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
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
import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.ui.util.CurrencyFormatter
import com.alabadla.internetcardledger.domain.ArabicSearch
import com.alabadla.internetcardledger.domain.SalePaymentRules
import com.alabadla.internetcardledger.ui.viewmodel.SaleFormError
import com.alabadla.internetcardledger.ui.viewmodel.SaleFormState
import com.alabadla.internetcardledger.ui.viewmodel.SalesViewModel
import kotlinx.coroutines.launch
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
            onCardTypeChanged = viewModel::updateCardType,
            onQuantityChanged = viewModel::updateQuantity,
            onPaymentStatusChanged = viewModel::updatePaymentStatus,
            onCustomPaidAmountChanged = viewModel::updateCustomPaidAmount,
            onCreateAndSelectCustomer = viewModel::createAndSelectCustomer,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sale.description, style = MaterialTheme.typography.titleLarge)
                PaymentStatusChip(status = sale.paymentStatus)
            }

            val totalAmount = sale.quantity.toLong() * sale.unitPriceMinor
            val totalStr = CurrencyFormatter.formatMoney(totalAmount)
            Text(
                stringResource(R.string.sale_list_summary, sale.quantity, totalStr),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun PaymentStatusChip(status: PaymentStatus) {
    val (label, containerColor, contentColor) = when (status) {
        PaymentStatus.PAID -> Triple(
            stringResource(R.string.payment_status_paid),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PaymentStatus.UNPAID -> Triple(
            stringResource(R.string.payment_status_unpaid),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        PaymentStatus.PARTIALLY_PAID -> Triple(
            stringResource(R.string.payment_status_partially_paid),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }

    ElevatedCard(
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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
                val totalAmount = sale.quantity.toLong() * sale.unitPriceMinor
                DetailRow(
                    R.string.label_total_amount,
                    CurrencyFormatter.formatMoney(totalAmount),
                )
                DetailRow(R.string.label_payment_status, stringResource(sale.paymentStatus.labelResource))
                DetailRow(R.string.label_paid_amount, CurrencyFormatter.formatMoney(sale.paidAmountMinor))
                DetailRow(R.string.label_remaining_amount, CurrencyFormatter.formatMoney(totalAmount - sale.paidAmountMinor))
                DetailRow(R.string.sale_date, formatDate(sale.soldAt))

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
    onCardTypeChanged: (CardType) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onPaymentStatusChanged: (PaymentStatus) -> Unit,
    onCustomPaidAmountChanged: (String) -> Unit,
    onCreateAndSelectCustomer: suspend (name: String, phone: String?) -> String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showCustomerPicker by rememberSaveable { mutableStateOf(false) }
    var showNewCustomerDialog by rememberSaveable { mutableStateOf(false) }

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
            // Section 1: Customer Selection Button
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val selectedCustomer = customers.find { it.id == state.customerId }
                    val selectedName = selectedCustomer?.name ?: stringResource(R.string.no_customer_selected)

                    Text(
                        text = stringResource(R.string.selected_customer_value, selectedName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Button(
                        onClick = { showCustomerPicker = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.action_select_customer))
                        Text(
                            text = stringResource(R.string.action_select_customer),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            // Section 2: Card Type Selection (6 hours vs 12 hours)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.label_select_card_type),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardType.entries.forEach { cardType ->
                            val selected = state.cardType == cardType
                            val label = stringResource(cardType.labelResource)
                            if (selected) {
                                Button(
                                    onClick = { onCardTypeChanged(cardType) },
                                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onCardTypeChanged(cardType) },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }

                    // Quantity Input Field
                    OutlinedTextField(
                        value = state.quantity,
                        onValueChange = onQuantityChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sale_quantity_required)) },
                        placeholder = { Text(stringResource(R.string.quantity_example)) },
                        enabled = !state.isSaving,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )

                    // Automatic Calculation Display
                    val qty = SalesViewModel.normalizeNumber(state.quantity).toIntOrNull() ?: 0
                    val unitPrice = state.cardType.priceMinor
                    val totalAmount = if (qty > 0) runCatching {
                        SalePaymentRules.total(qty, state.cardType)
                    }.getOrDefault(0L) else 0L
                    val totalStr = CurrencyFormatter.formatMoney(totalAmount)
                    val unitPriceStr = CurrencyFormatter.formatMoney(unitPrice)

                    Text(
                        text = stringResource(R.string.calculation_display, qty, unitPriceStr, totalStr),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // Section 3: Payment Status
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.label_payment_status),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentStatus.entries.forEach { status ->
                            val selected = state.paymentStatus == status
                            val label = stringResource(status.labelResource)
                            if (selected) {
                                Button(
                                    onClick = { onPaymentStatusChanged(status) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onPaymentStatusChanged(status) },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }

                    if (state.paymentStatus == PaymentStatus.PARTIALLY_PAID) {
                        OutlinedTextField(
                            value = state.customPaidAmountMinor,
                            onValueChange = onCustomPaidAmountChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.label_paid_amount)) },
                            placeholder = { Text(stringResource(R.string.paid_amount_example)) },
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

                        val customPaid = SalesViewModel.normalizeNumber(state.customPaidAmountMinor).toLongOrNull() ?: 0L
                        val qty = SalesViewModel.normalizeNumber(state.quantity).toIntOrNull() ?: 0
                        val total = if (qty > 0) runCatching {
                            SalePaymentRules.total(qty, state.cardType)
                        }.getOrDefault(0L) else 0L
                        val remaining = (total - customPaid).coerceAtLeast(0L)

                        Text(
                            text = stringResource(R.string.remaining_amount_value, CurrencyFormatter.formatMoney(remaining)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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

    if (showCustomerPicker) {
        CustomerSelectionDialog(
            customers = customers,
            selectedCustomerId = state.customerId,
            onSelectCustomer = { customerId ->
                onCustomerIdChanged(customerId)
                showCustomerPicker = false
            },
            onCreateNewCustomer = {
                showCustomerPicker = false
                showNewCustomerDialog = true
            },
            onDismiss = { showCustomerPicker = false },
        )
    }

    if (showNewCustomerDialog) {
        NewCustomerModal(
            onCreateAndSelect = onCreateAndSelectCustomer,
            onDismiss = { showNewCustomerDialog = false },
        )
    }
}

@Composable
private fun CustomerSelectionDialog(
    customers: List<CustomerEntity>,
    selectedCustomerId: String?,
    onSelectCustomer: (String?) -> Unit,
    onCreateNewCustomer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredCustomers by remember(customers, searchQuery) {
        derivedStateOf {
            customers.filter { ArabicSearch.customerMatches(it.name, it.phone, searchQuery) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_select_customer_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_customer_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_customer_placeholder))
                    },
                    singleLine = true,
                )

                Button(
                    onClick = onCreateNewCustomer,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_create_new_customer))
                    Text(
                        text = stringResource(R.string.action_create_new_customer),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        val isNoneSelected = selectedCustomerId == null
                        if (isNoneSelected) {
                            Button(
                                onClick = { onSelectCustomer(null) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.no_customer_selected)) }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectCustomer(null) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.no_customer_selected)) }
                        }
                    }

                    items(filteredCustomers, key = { it.id }) { customer ->
                        val isSelected = selectedCustomerId == customer.id
                        if (isSelected) {
                            Button(
                                onClick = { onSelectCustomer(customer.id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(customer.name) }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectCustomer(customer.id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(customer.name) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun NewCustomerModal(
    onCreateAndSelect: suspend (name: String, phone: String?) -> String?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.add_customer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.customer_name_required)) },
                    placeholder = { Text(stringResource(R.string.customer_name_example)) },
                    singleLine = true,
                )
                if (saveFailed) {
                    Text(
                        text = stringResource(R.string.error_customer_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.customer_phone_label)) },
                    placeholder = { Text(stringResource(R.string.customer_phone_example)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || isSaving) return@Button
                    isSaving = true
                    scope.launch {
                        val createdId = onCreateAndSelect(name, phone.ifBlank { null })
                        isSaving = false
                        if (createdId != null) onDismiss() else saveFailed = true
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun SaleFormError.stringResource(): Int = when (this) {
    SaleFormError.QUANTITY_REQUIRED -> R.string.error_quantity_required
    SaleFormError.QUANTITY_INVALID -> R.string.error_quantity_invalid
    SaleFormError.PRICE_INVALID -> R.string.error_price_invalid
    SaleFormError.PAID_AMOUNT_INVALID -> R.string.error_paid_amount_invalid
    SaleFormError.SAVE_FAILED -> R.string.error_save_failed
}

private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
    Locale.forLanguageTag("ar"),
).format(Date(timestamp))
