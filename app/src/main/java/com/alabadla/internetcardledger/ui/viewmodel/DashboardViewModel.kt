package com.alabadla.internetcardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.data.repository.PaymentRepository
import com.alabadla.internetcardledger.data.repository.SaleRepository
import com.alabadla.internetcardledger.domain.HistoryDateFilter
import com.alabadla.internetcardledger.domain.HistoryRecord
import com.alabadla.internetcardledger.domain.HistoryRecordType
import com.alabadla.internetcardledger.domain.LedgerInsights
import com.alabadla.internetcardledger.domain.LedgerOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Content(val overview: LedgerOverview) : DashboardUiState
    data object Error : DashboardUiState
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val records: List<HistoryRecord> = emptyList(),
    val query: String = "",
    val type: HistoryRecordType = HistoryRecordType.ALL,
    val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
    val selectedCalendarDate: Long? = null,
)

class DashboardViewModel(
    customerRepository: CustomerRepository,
    saleRepository: SaleRepository,
    paymentRepository: PaymentRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val recordType = MutableStateFlow(HistoryRecordType.ALL)
    private val dateFilter = MutableStateFlow(HistoryDateFilter.ALL)
    private val selectedCalendarDate = MutableStateFlow<Long?>(null)

    val dashboardState: StateFlow<DashboardUiState> = combine(
        customerRepository.observeAllCustomers(),
        saleRepository.observeAllSales(),
        paymentRepository.observeAllPayments(),
    ) { customers, sales, payments ->
        DashboardUiState.Content(
            LedgerInsights.calculate(
                customers = customers,
                sales = sales,
                payments = payments,
            ),
        ) as DashboardUiState
    }.catch {
        emit(DashboardUiState.Error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState.Loading,
    )

    val historyState: StateFlow<HistoryUiState> = combine(
        dashboardState,
        searchQuery,
        recordType,
        dateFilter,
        selectedCalendarDate,
    ) { dashboard, query, type, date, calendarDate ->
        when (dashboard) {
            DashboardUiState.Loading -> HistoryUiState(
                isLoading = true,
                query = query,
                type = type,
                dateFilter = date,
                selectedCalendarDate = calendarDate,
            )
            DashboardUiState.Error -> HistoryUiState(
                isLoading = false,
                hasError = true,
                query = query,
                type = type,
                dateFilter = date,
                selectedCalendarDate = calendarDate,
            )
            is DashboardUiState.Content -> HistoryUiState(
                isLoading = false,
                records = LedgerInsights.filterHistory(
                    records = dashboard.overview.allHistory,
                    query = query,
                    type = type,
                    dateFilter = date,
                    selectedCalendarDate = calendarDate,
                    now = currentTimeMillis(),
                ),
                query = query,
                type = type,
                dateFilter = date,
                selectedCalendarDate = calendarDate,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun updateRecordType(value: HistoryRecordType) {
        recordType.value = value
    }

    fun updateDateFilter(value: HistoryDateFilter) {
        dateFilter.value = value
    }

    fun updateCalendarDate(value: Long?) {
        selectedCalendarDate.value = value
    }

    companion object {
        fun factory(
            customerRepository: CustomerRepository,
            saleRepository: SaleRepository,
            paymentRepository: PaymentRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    customerRepository = customerRepository,
                    saleRepository = saleRepository,
                    paymentRepository = paymentRepository,
                )
            }
        }
    }
}
