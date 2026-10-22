package com.alabadla.internetcardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.data.repository.SaleRepository
import com.alabadla.internetcardledger.domain.SalePaymentRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SaleFormState(
    val customerId: String? = null,
    val cardType: CardType = CardType.SIX_HOURS,
    val quantity: String = "1",
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val customPaidAmountMinor: String = "",
    val editingSale: SaleEntity? = null,
    val error: SaleFormError? = null,
    val isSaving: Boolean = false,
    val savedSaleId: String? = null,
)

enum class SaleFormError {
    QUANTITY_REQUIRED,
    QUANTITY_INVALID,
    PRICE_INVALID,
    PAID_AMOUNT_INVALID,
    SAVE_FAILED,
}

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModel(
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository? = null,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedSaleId = MutableStateFlow<String?>(null)
    private val _formState = MutableStateFlow(SaleFormState())
    private val _isDeleting = MutableStateFlow(false)
    private val _deletedSaleId = MutableStateFlow<String?>(null)

    val customers: StateFlow<List<CustomerEntity>> = (customerRepository?.observeCustomers() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sales: StateFlow<List<SaleEntity>> = searchQuery
        .flatMapLatest { query -> saleRepository.observeSales(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query: StateFlow<String> = searchQuery
    val formState: StateFlow<SaleFormState> = _formState
    val isDeleting: StateFlow<Boolean> = _isDeleting
    val deletedSaleId: StateFlow<String?> = _deletedSaleId

    val selectedSale: StateFlow<SaleEntity?> = selectedSaleId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else saleRepository.observeSale(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectSale(id: String) {
        selectedSaleId.value = id
    }

    fun beginCreate() {
        selectedSaleId.value = null
        _formState.value = SaleFormState()
    }

    fun beginEdit(sale: SaleEntity) {
        selectedSaleId.value = sale.id
        _formState.value = SaleFormState(
            customerId = sale.customerId,
            cardType = sale.cardType,
            quantity = sale.quantity.toString(),
            paymentStatus = sale.paymentStatus,
            customPaidAmountMinor = sale.paidAmountMinor.toString(),
            editingSale = sale,
        )
    }

    fun updateCustomerId(value: String?) = updateForm { copy(customerId = value, error = null) }
    fun updateCardType(cardType: CardType) = updateForm { copy(cardType = cardType, error = null) }
    fun updateQuantity(value: String) = updateForm { copy(quantity = value, error = null) }
    fun updatePaymentStatus(status: PaymentStatus) = updateForm { copy(paymentStatus = status, error = null) }
    fun updateCustomPaidAmount(value: String) = updateForm { copy(customPaidAmountMinor = value, error = null) }

    suspend fun createAndSelectCustomer(name: String, phone: String? = null): String? {
        val repo = customerRepository ?: return null
        return runCatching {
            val newCustId = repo.addCustomer(name = name, phone = phone)
            updateCustomerId(newCustId)
            newCustId
        }.getOrNull()
    }

    fun saveSale() {
        val current = _formState.value
        if (current.isSaving) return

        val quantityText = normalizeNumber(current.quantity)
        val quantity = quantityText.toIntOrNull()
        val unitPrice = current.cardType.priceMinor
        val totalAmount = if (quantity != null && quantity > 0) {
            runCatching { SalePaymentRules.total(quantity, current.cardType) }.getOrNull()
        } else null

        val partialAmount = normalizeNumber(current.customPaidAmountMinor).toLongOrNull()
        val paidAmount = totalAmount?.let {
            runCatching { SalePaymentRules.paidAmount(it, current.paymentStatus, partialAmount) }.getOrNull()
        }

        val error = when {
            quantityText.isEmpty() -> SaleFormError.QUANTITY_REQUIRED
            quantity == null || quantity <= 0 -> SaleFormError.QUANTITY_INVALID
            totalAmount == null -> SaleFormError.PRICE_INVALID
            paidAmount == null -> SaleFormError.PAID_AMOUNT_INVALID
            else -> null
        }

        if (error != null) {
            _formState.update { it.copy(error = error) }
            return
        }

        val description = current.cardType.storageDescription

        _formState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val editingSale = current.editingSale
                if (editingSale == null) {
                    saleRepository.addSale(
                        customerId = current.customerId,
                        description = description,
                        quantity = requireNotNull(quantity),
                        unitPriceMinor = unitPrice,
                        cardType = current.cardType,
                        paymentStatus = current.paymentStatus,
                        paidAmountMinor = requireNotNull(paidAmount),
                    )
                } else {
                    saleRepository.updateSale(
                        editingSale.copy(
                            customerId = current.customerId,
                            description = description,
                            quantity = requireNotNull(quantity),
                            unitPriceMinor = unitPrice,
                            cardType = current.cardType,
                            paymentStatus = current.paymentStatus,
                            paidAmountMinor = requireNotNull(paidAmount),
                        ),
                    )
                    editingSale.id
                }
            }.onSuccess { id ->
                selectedSaleId.value = id
                _formState.update { it.copy(isSaving = false, savedSaleId = id) }
            }.onFailure {
                _formState.update {
                    it.copy(isSaving = false, error = SaleFormError.SAVE_FAILED)
                }
            }
        }
    }

    fun clearSaveResult() {
        _formState.update { it.copy(savedSaleId = null) }
    }

    fun deleteSelectedSale() {
        val id = selectedSaleId.value ?: return
        if (_isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            runCatching { saleRepository.deleteSale(id) }
                .onSuccess { deleted ->
                    if (deleted) {
                        selectedSaleId.value = null
                        _deletedSaleId.value = id
                    }
                }
            _isDeleting.value = false
        }
    }

    fun clearDeleteResult() {
        _deletedSaleId.value = null
    }

    private inline fun updateForm(update: SaleFormState.() -> SaleFormState) {
        if (!_formState.value.isSaving) _formState.update(update)
    }

    companion object {
        fun factory(
            saleRepository: SaleRepository,
            customerRepository: CustomerRepository? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SalesViewModel(saleRepository, customerRepository) }
        }

        internal fun normalizeNumber(value: String): String = buildString(value.length) {
            value.trim().forEach { character ->
                append(
                    when (character) {
                        in '\u0660'..'\u0669' -> '0' + (character - '\u0660')
                        in '\u06F0'..'\u06F9' -> '0' + (character - '\u06F0')
                        else -> character
                    },
                )
            }
        }
    }
}
