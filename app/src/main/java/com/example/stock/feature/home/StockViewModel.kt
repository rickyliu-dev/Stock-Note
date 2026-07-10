package com.example.stock.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.data.source.StockFetcher
import com.example.stock.core.domain.GetStockInventoryUseCase
import com.example.stock.feature.home.component.PortfolioSummary
import com.example.stock.feature.home.component.StockDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val stockFetcher: StockFetcher,
    private val getStockInventoryUseCase: GetStockInventoryUseCase,
    private val importExcelUseCase: com.example.stock.core.domain.ImportExcelUseCase
) : ViewModel() {

    fun importFromExcel(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val items = importExcelUseCase.execute(context, uri)
                if (items.isNotEmpty()) {
                    val timestamp = System.currentTimeMillis()
                    val accountName = "匯入帳戶_$timestamp"
                    val newId = repository.importTransactionsToNewAccount(accountName, items)
                    settingsRepository.setCurrentAccountId(newId)
                    toastMessage.emit("成功匯入 ${items.size} 筆資料至新帳戶")
                } else {
                    toastMessage.emit("Excel 檔案中無有效資料")
                }
            } catch (e: Exception) {
                toastMessage.emit("匯入失敗: ${e.message}")
            }
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 暴露給 HomeScreen 的屬性 (相容舊代碼)
    val transactions: StateFlow<List<TransactionItem>> = repository.transactionsDesc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val allAccounts: StateFlow<List<Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val portfolioSummary: StateFlow<PortfolioSummary> = uiState.map { it.summary }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioSummary())
        
    val searchQuery: StateFlow<String> = uiState.map { it.searchQuery }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        
    val currentAccountId: StateFlow<Long> = uiState.map { it.currentAccountId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1L)
        
    val includeDividends: StateFlow<Boolean> = settingsRepository.includeDividendsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val assetDistribution = MutableStateFlow<Map<String, Double>>(emptyMap())
    val assetHistory = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val isUpdating = MutableStateFlow(false)
    
    val activeStockList: StateFlow<List<StockDisplayItem>> = uiState.map { it.activeList }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val closedStockList: StateFlow<List<StockDisplayItem>> = uiState.map { it.closedList }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val isCashManagementEnabled = MutableStateFlow(false)
    val lastUpdateTimestamp = MutableStateFlow(0L)
    val toastMessage = MutableSharedFlow<String>()

    // Compose State for simple UI toggles
    private val _isCumulative = MutableStateFlow(true)
    val isCumulativeFlow = _isCumulative.asStateFlow()
    var isCumulative by mutableStateOf(true)
        private set
    
    fun toggleProfitMode() {
        isCumulative = !isCumulative
        _isCumulative.value = isCumulative
    }

    var isSelectionMode by mutableStateOf(false)
    private val _selectedSymbols = mutableStateOf(setOf<String>())
    val selectedSymbols: Set<String> get() = _selectedSymbols.value

    private val _stockQuotes = MutableStateFlow<Map<String, StockQuote>>(emptyMap())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTabIndex = MutableStateFlow(0)

    init {
        setupDataStreams()
        loadInitialData()
        startAutoUpdateCycle()
    }

    private fun setupDataStreams() {
        combine(
            settingsRepository.currentAccountIdFlow,
            repository.transactionsDesc,
            _stockQuotes,
            settingsRepository.twSettingsFlow,
            settingsRepository.costBasisMethodFlow,
            settingsRepository.includeDividendsFlow,
            repository.allAccounts,
            _searchQuery,
            _selectedTabIndex,
            _isCumulative
        ) { args ->
            val accId = args[0] as Long
            val allTx = args[1] as List<TransactionItem>
            val quotes = args[2] as Map<String, StockQuote>
            val settings = args[3] as SettingsRepository.TwSettings
            val method = args[4] as CostBasisMethod
            val includeDiv = args[5] as Boolean
            val accounts = args[6] as List<Account>
            val query = args[7] as String
            val tabIndex = args[8] as Int
            val isCum = args[9] as Boolean

            val accountTx = allTx.filter { it.accountId == accId }
            val currentAccount = accounts.find { it.id == accId }
            
            val calcResult = getStockInventoryUseCase(
                allTransactions = accountTx,
                quotes = quotes,
                settings = settings,
                method = method,
                includeDividends = includeDiv
            )

            val fullStockList = calcResult.positions.values.map { pos ->
                val currentProfit = if (tabIndex == 0) {
                    // 持股中：基礎 (未實現 + 若累積則加已實現) + 若含息則加股息
                    val base = pos.unrealizedProfit + (if (isCum) pos.realizedProfit else 0.0)
                    if (includeDiv) base + pos.dividendAmount else base
                } else {
                    // 已清倉：已實現 + 若含息則加股息
                    if (includeDiv) pos.realizedProfit + pos.dividendAmount else pos.realizedProfit
                }

                StockDisplayItem(
                    symbol = pos.symbol,
                    name = pos.name,
                    shares = pos.shares,
                    currentPrice = pos.currentPrice,
                    change = pos.currentPrice - pos.yesterdayPrice,
                    changePercent = if (pos.yesterdayPrice > 0) ((pos.currentPrice - pos.yesterdayPrice) / pos.yesterdayPrice) * 100 else 0.0,
                    avgCost = pos.avgCost,
                    profit = currentProfit,
                    profitPercent = when {
                        tabIndex == 0 -> {
                            val denominator = if (isCum) {
                                pos.totalBuyAmount
                            } else {
                                if (pos.shares > 0.0001) pos.totalCost else pos.totalBuyAmount
                            }
                            if (denominator > 0.01) (currentProfit / denominator) * 100 else 0.0
                        }
                        else -> {
                            if (pos.totalBuyAmount > 0.01) (currentProfit / pos.totalBuyAmount) * 100 else 0.0
                        }
                    },
                    marketValue = pos.marketValue
                )
            }

            val activeList = fullStockList.filter { it.shares > 0 }
            val closedList = fullStockList.filter { it.shares <= 0 }
            
            val filteredList = if (tabIndex == 0) activeList else closedList
            val finalDisplayList = filteredList.filter { 
                it.symbol.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) 
            }

            val initialBalance = currentAccount?.initialBalance ?: 0.0
            val cashEnabled = currentAccount?.isCashManagementEnabled ?: false
            isCashManagementEnabled.value = cashEnabled
            val cashBalance = if (cashEnabled) initialBalance + calcResult.totalCashFlow else 0.0
            
            val holdingsGrossValue = calcResult.positions.values
                .filter { it.shares > 0 }
                .sumOf { it.marketValue }
            
            val totalAssets = holdingsGrossValue + cashBalance

            val summary = PortfolioSummary(
                totalAssets = totalAssets,
                cashBalance = cashBalance,
                unrealizedProfit = calcResult.positions.values.filter { it.shares > 0 }.sumOf { pos ->
                    val base = pos.unrealizedProfit + (if (isCum) pos.realizedProfit else 0.0)
                    if (includeDiv) base + pos.dividendAmount else base
                },
                cumulativeProfit = calcResult.portfolioUnrealizedProfit + calcResult.totalRealizedProfit + 
                    (if (includeDiv) calcResult.positions.values.sumOf { it.dividendAmount } else 0.0),
                dailyProfit = calcResult.portfolioDailyProfit,
                dailyRate = if (totalAssets - calcResult.portfolioDailyProfit > 0) 
                    (calcResult.portfolioDailyProfit / (totalAssets - calcResult.portfolioDailyProfit)) * 100 else 0.0
            )

            assetDistribution.value = calcResult.positions.values
                .filter { it.shares > 0 }
                .associate { it.name to it.marketValue }

            HomeUiState(
                summary = summary,
                stockList = finalDisplayList,
                activeList = activeList,
                closedList = closedList,
                isLoading = false,
                selectedTabIndex = tabIndex,
                currentAccountId = accId,
                searchQuery = query
            )
        }.onEach { newState ->
            _uiState.update { newState }
        }.launchIn(viewModelScope)
    }

    fun selectTab(index: Int) { _selectedTabIndex.value = index }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    
    fun updateAllPrices() {
        if (isUpdating.value) return
        isUpdating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val symbols = _uiState.value.activeList.map { it.symbol }.distinct()
                if (symbols.isEmpty()) return@launch
                
                val newQuotes = _stockQuotes.value.toMutableMap()
                symbols.forEach { symbol ->
                    stockFetcher.fetchStockDetail(symbol)?.let {
                        newQuotes[symbol] = StockQuote(it.currentPrice, it.change, it.changePercent)
                    }
                    delay(500)
                }
                _stockQuotes.value = newQuotes
                repository.savePriceCache(newQuotes)
                lastUpdateTimestamp.value = System.currentTimeMillis()
            } finally {
                isUpdating.value = false
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _stockQuotes.value = repository.getPriceCache()
        }
    }

    private fun startAutoUpdateCycle() {
        viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000)
                updateAllPrices()
            }
        }
    }
    
    fun setCurrentAccount(id: Long) {
        viewModelScope.launch { settingsRepository.setCurrentAccountId(id) }
    }
    
    fun deleteStock(symbol: String) {
        viewModelScope.launch {
            val txs = repository.transactionsDesc.first().filter { it.symbol == symbol }
            repository.deleteTransactionsByIds(txs.map { it.id })
            toastMessage.emit("已刪除 $symbol 的所有紀錄")
        }
    }

    fun deleteTransaction(item: TransactionItem) {
        viewModelScope.launch { repository.deleteTransactionsByIds(listOf(item.id)) }
    }

    fun toggleIncludeDividends(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludeDividends(enabled) }
    }

    fun enterSelectionMode(symbol: String) {
        isSelectionMode = true
        _selectedSymbols.value = setOf(symbol)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        _selectedSymbols.value = emptySet()
    }

    fun toggleSelection(symbol: String) {
        val current = _selectedSymbols.value.toMutableSet()
        if (current.contains(symbol)) {
            current.remove(symbol)
            if (current.isEmpty()) exitSelectionMode()
        } else {
            current.add(symbol)
        }
        _selectedSymbols.value = current
    }

    fun deleteSelectedStocks() {
        viewModelScope.launch {
            val symbols = selectedSymbols.toList()
            val txs = repository.transactionsDesc.first().filter { symbols.contains(it.symbol) }
            repository.deleteTransactionsByIds(txs.map { it.id })
            exitSelectionMode()
            toastMessage.emit("已刪除 ${symbols.size} 支股票")
        }
    }

    fun moveSelectedStocks(targetAccountId: Long) {
        viewModelScope.launch {
            val symbols = selectedSymbols.toList()
            val allTxs = repository.transactionsDesc.first()
            val txsToMove = allTxs.filter { it.accountId == _uiState.value.currentAccountId && symbols.contains(it.symbol) }
            val updatedTxs = txsToMove.map { it.copy(accountId = targetAccountId) }
            repository.upsertTransactions(updatedTxs)
            exitSelectionMode()
            toastMessage.emit("已將 ${symbols.size} 支股票移動至目標帳戶")
        }
    }

    fun verifyAndUnlock(code: String): Boolean {
        if (code == "8888") {
            viewModelScope.launch {
                toastMessage.emit("解鎖成功！")
            }
            return true
        } else {
            viewModelScope.launch {
                toastMessage.emit("驗證碼錯誤")
            }
            return false
        }
    }
}

data class HomeUiState(
    val summary: PortfolioSummary = PortfolioSummary(),
    val stockList: List<StockDisplayItem> = emptyList(),
    val activeList: List<StockDisplayItem> = emptyList(),
    val closedList: List<StockDisplayItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTabIndex: Int = 0,
    val currentAccountId: Long = 1L,
    val searchQuery: String = ""
)
