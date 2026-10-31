package com.example.stock.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.domain.DividendStatistics
import com.example.stock.core.domain.GetStockInventoryUseCase
import com.example.stock.core.domain.RealizedProfitStatistics
import com.example.stock.core.domain.ReturnStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class DividendUiState(
    val isLoading: Boolean = true,
    val years: List<DividendStatistics.YearDividend> = emptyList(),
    val selected: DividendStatistics.YearDividend? = null,
    val allYearsTotal: Double = 0.0
)

data class RealizedUiState(
    val isLoading: Boolean = true,
    val years: List<RealizedProfitStatistics.YearRealized> = emptyList(),
    val selected: RealizedProfitStatistics.YearRealized? = null,
    val allYearsTotal: Double = 0.0,
    val costBasisLabel: String = ""
)

data class ReturnUiState(
    val isLoading: Boolean = true,
    val portfolio: ReturnStatistics.PortfolioReturn? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportViewModel @Inject constructor(
    repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    getStockInventoryUseCase: GetStockInventoryUseCase
) : ViewModel() {

    // 只統計目前帳戶的交易
    private val accountTransactions = settingsRepository.currentAccountIdFlow
        .flatMapLatest { repository.transactionsByAccount(it) }

    // 目前選中的報表分頁
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    // null 代表使用最新的年份
    private val _selectedRealizedYear = MutableStateFlow<Int?>(null)

    val realizedState: StateFlow<RealizedUiState> = combine(
        combine(accountTransactions, settingsRepository.costBasisMethodFlow) { txs, method ->
            method to RealizedProfitStatistics.calculate(
                getStockInventoryUseCase.calculateBase(txs, method).realizedEvents
            )
        },
        _selectedRealizedYear
    ) { (method, years), selectedYear ->
        RealizedUiState(
            isLoading = false,
            years = years,
            selected = years.find { it.year == selectedYear } ?: years.firstOrNull(),
            allYearsTotal = years.sumOf { it.total },
            costBasisLabel = method.label
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RealizedUiState())

    fun selectRealizedYear(year: Int) {
        _selectedRealizedYear.value = year
    }

    // 報酬率：股價使用首頁最後一次更新存下的快取
    val returnState: StateFlow<ReturnUiState> = combine(
        accountTransactions,
        settingsRepository.costBasisMethodFlow,
        repository.stockPricesFlow,
        settingsRepository.twSettingsFlow
    ) { txs, method, quotes, settings ->
        val base = getStockInventoryUseCase.calculateBase(txs, method)
        val result = getStockInventoryUseCase.evaluate(base, quotes, settings)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        ReturnUiState(
            isLoading = false,
            portfolio = ReturnStatistics.calculate(base, result.positions, today)
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReturnUiState())

    // null 代表使用最新的年份
    private val _selectedDividendYear = MutableStateFlow<Int?>(null)

    val dividendState: StateFlow<DividendUiState> = combine(
        accountTransactions.map { DividendStatistics.calculate(it) },
        _selectedDividendYear
    ) { years, selectedYear ->
        DividendUiState(
            isLoading = false,
            years = years,
            selected = years.find { it.year == selectedYear } ?: years.firstOrNull(),
            allYearsTotal = years.sumOf { it.total }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DividendUiState())

    fun selectDividendYear(year: Int) {
        _selectedDividendYear.value = year
    }
}
