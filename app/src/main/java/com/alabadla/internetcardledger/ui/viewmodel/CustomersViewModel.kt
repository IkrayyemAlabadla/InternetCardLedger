package com.alabadla.internetcardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.data.repository.PaymentRepository
import com.alabadla.internetcardledger.data.repository.SaleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomerSummary(
    val customer: CustomerEntity,
    val totalSalesMinor: Long,
    val totalPaymentsMinor: Long,
    val balanceMinor: Long,
    val salesCount: Int,
    val paymentsCount: Int,
)

data class CustomerDetailsState(
    val customer: CustomerEntity,
    val totalSalesMinor: Long,
    val totalPaymentsMinor: Long,
    val balanceMinor: Long,
    val sales: List<SaleEntity>,
    val payments: List<PaymentEntity>,
)

data class CustomerFormState(
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    val editingCustomer: CustomerEntity? = null,
    val error: CustomerFormError? = null,
    val isSaving: Boolean = false,
    val savedCustomerId: String? = null,
)

enum class CustomerFormError {
    NAME_REQUIRED,
    SAVE_FAILED,
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModel(
    private val customerRepository: CustomerRepository,
    private val saleRepository: SaleRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCustomerId = MutableStateFlow<String?>(null)
    private val _formState = MutableStateFlow(CustomerFormState())
    private val _isDeleting = MutableStateFlow(false)
    private val _deletedCustomerId = MutableStateFlow<String?>(null)

    val query: StateFlow<String> = searchQuery
    val formState: StateFlow<CustomerFormState> = _formState
    val isDeleting: StateFlow<Boolean> = _isDeleting
    val deletedCustomerId: StateFlow<String?> = _deletedCustomerId

    val customerSummaries: StateFlow<List<CustomerSummary>> = combine(
        searchQuery.flatMapLatest { query -> customerRepository.observeCustomers(query) },
        saleRepository.observeSales(),
        paymentRepository.observePayments(),
    ) { customers, sales, payments ->
        val salesByCustomer = sales.groupBy { it.customerId }
        val paymentsByCustomer = payments.groupBy { it.customerId }

        customers.map { customer ->
            val customerSales = salesByCustomer[customer.id].orEmpty()
            val customerPayments = paymentsByCustomer[customer.id].orEmpty()

            val totalSales = customerSales.sumOf { it.quantity.toLong() * it.unitPriceMinor }
            val totalPayments = customerSales.sumOf { it.paidAmountMinor } +
                customerPayments.sumOf { it.amountMinor }

            CustomerSummary(
                customer = customer,
                totalSalesMinor = totalSales,
                totalPaymentsMinor = totalPayments,
                balanceMinor = totalSales - totalPayments,
                salesCount = customerSales.size,
                paymentsCount = customerPayments.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCustomerDetails: StateFlow<CustomerDetailsState?> = combine(
        selectedCustomerId.flatMapLatest { id ->
            if (id == null) flowOf(null) else customerRepository.observeCustomer(id)
        },
        saleRepository.observeSales(),
        paymentRepository.observePayments(),
    ) { customer, sales, payments ->
        if (customer == null) null
        else {
            val customerSales = sales.filter { it.customerId == customer.id }
            val customerPayments = payments.filter { it.customerId == customer.id }

            val totalSales = customerSales.sumOf { it.quantity.toLong() * it.unitPriceMinor }
            val totalPayments = customerSales.sumOf { it.paidAmountMinor } +
                customerPayments.sumOf { it.amountMinor }

            CustomerDetailsState(
                customer = customer,
                totalSalesMinor = totalSales,
                totalPaymentsMinor = totalPayments,
                balanceMinor = totalSales - totalPayments,
                sales = customerSales,
                payments = customerPayments,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectCustomer(id: String?) {
        selectedCustomerId.value = id
    }

    fun beginCreate() {
        selectedCustomerId.value = null
        _formState.value = CustomerFormState()
    }

    fun beginEdit(customer: CustomerEntity) {
        selectedCustomerId.value = customer.id
        _formState.value = CustomerFormState(
            name = customer.name,
            phone = customer.phone.orEmpty(),
            notes = customer.notes.orEmpty(),
            editingCustomer = customer,
        )
    }

    fun updateName(name: String) = updateForm { copy(name = name, error = null) }
    fun updatePhone(phone: String) = updateForm { copy(phone = phone, error = null) }
    fun updateNotes(notes: String) = updateForm { copy(notes = notes, error = null) }

    fun saveCustomer() {
        val current = _formState.value
        if (current.isSaving) return

        val name = current.name.trim()
        if (name.isEmpty()) {
            _formState.update { it.copy(error = CustomerFormError.NAME_REQUIRED) }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val editing = current.editingCustomer
                if (editing == null) {
                    customerRepository.addCustomer(
                        name = name,
                        phone = current.phone,
                        notes = current.notes,
                    )
                } else {
                    customerRepository.updateCustomer(
                        editing.copy(
                            name = name,
                            phone = current.phone.trim().takeIf(String::isNotEmpty),
                            notes = current.notes.trim().takeIf(String::isNotEmpty),
                        ),
                    )
                    editing.id
                }
            }.onSuccess { id ->
                selectedCustomerId.value = id
                _formState.update { it.copy(isSaving = false, savedCustomerId = id) }
            }.onFailure {
                _formState.update { it.copy(isSaving = false, error = CustomerFormError.SAVE_FAILED) }
            }
        }
    }

    fun clearSaveResult() {
        _formState.update { it.copy(savedCustomerId = null) }
    }

    fun deleteSelectedCustomer() {
        val id = selectedCustomerId.value ?: return
        if (_isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            runCatching { customerRepository.deleteCustomer(id) }
                .onSuccess { deleted ->
                    if (deleted) {
                        selectedCustomerId.value = null
                        _deletedCustomerId.value = id
                    }
                }
            _isDeleting.value = false
        }
    }

    fun clearDeleteResult() {
        _deletedCustomerId.value = null
    }

    private inline fun updateForm(update: CustomerFormState.() -> CustomerFormState) {
        if (!_formState.value.isSaving) _formState.update(update)
    }

    companion object {
        fun factory(
            customerRepository: CustomerRepository,
            saleRepository: SaleRepository,
            paymentRepository: PaymentRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CustomersViewModel(
                    customerRepository = customerRepository,
                    saleRepository = saleRepository,
                    paymentRepository = paymentRepository,
                )
            }
        }
    }
}
