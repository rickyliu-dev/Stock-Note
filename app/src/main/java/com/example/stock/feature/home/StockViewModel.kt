package com.example.stock.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.data.repository.StockRepository
import com.example.stock.core.domain.GetStockInventoryUseCase
import com.example.stock.feature.home.component.PortfolioSummary
import com.example.stock.feature.home.component.StockDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val stockRepository: StockRepository,
    private val getStockInventoryUseCase: GetStockInventoryUseCase,
    private val importExcelUseCase: com.example.stock.core.domain.ImportExcelUseCase
) : ViewModel() {

    fun importFromExcel(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val items = context.contentResolver.openInputStream(uri)?.let { importExcelUseCase.execute(it) } ?: emptyList()
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

    val allAccounts: StateFlow<List<Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val portfolioSummary: StateFlow<PortfolioSummary> = uiState.map { it.summary }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioSummary())
        
    // 搜尋框直接綁定輸入值，不等背景計算完成，避免打字掉字或游標跳動
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    init {
        setupDataStreams()
        loadInitialData()
        startAutoUpdateCycle()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupDataStreams() {
        // 第一層：交易紀錄 → 庫存 (最耗時，只在交易、帳戶或成本計算方式改變時重算)
        // 只向資料庫查詢目前帳戶的交易；切換帳戶時取消舊查詢
        val baseInventoryFlow = settingsRepository.currentAccountIdFlow
            .flatMapLatest { accId ->
                combine(
                    repository.transactionsByAccount(accId),
                    settingsRepository.costBasisMethodFlow
                ) { accountTx, method ->
                    accId to getStockInventoryUseCase.calculateBase(
                        allTransactions = accountTx,
                        method = method
                    )
                }
            }

        // 第二層：套用股價與手續費設定 (股價更新時只重算這層)
        val calcResultFlow = combine(
            baseInventoryFlow,
            _stockQuotes,
            settingsRepository.twSettingsFlow
        ) { (accId, base), quotes, settings ->
            accId to getStockInventoryUseCase.evaluate(base, quotes, settings)
        }

        // 第三層：顯示用的清單與總覽 (切換模式、搜尋時只重算這層)
        combine(
            calcResultFlow,
            settingsRepository.includeDividendsFlow,
            repository.allAccounts,
            _searchQuery,
            _isCumulative
        ) { (accId, calcResult), includeDiv, accounts, rawQuery, isCum ->
            val query = rawQuery.trim()
            val currentAccount = accounts.find { it.id == accId }

            val fullStockList = calcResult.positions.values.map { pos ->
                // 持股中與已清倉各自用自己的損益公式 (與目前選中哪個分頁無關)
                val isActive = pos.shares > 0
                val currentProfit = if (isActive) {
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
                        isActive -> {
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

            // 搜尋：代號或名稱包含關鍵字 (不分大小寫)，空白關鍵字則全部顯示
            val matchedList = if (query.isEmpty()) fullStockList else fullStockList.filter {
                it.symbol.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
            }
            val activeList = matchedList.filter { it.shares > 0 }
            val closedList = matchedList.filter { it.shares <= 0 }

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
                activeList = activeList,
                closedList = closedList,
                // 更新股價要涵蓋全部持股，不受搜尋篩選影響
                heldSymbols = calcResult.positions.values.filter { it.shares > 0 }.map { it.symbol },
                isLoading = false,
                currentAccountId = accId
            )
        }.flowOn(Dispatchers.Default)
        .onEach { newState ->
            _uiState.update { newState }
        }.launchIn(viewModelScope)
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun updateAllPrices() {
        if (isUpdating.value) return
        isUpdating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val symbols = _uiState.value.heldSymbols
                if (symbols.isEmpty()) return@launch

                val newQuotes = _stockQuotes.value.toMutableMap()
                symbols.forEach { symbol ->
                    stockRepository.fetchStockDetail(symbol)?.let {
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
    
    // 只取目前帳戶的交易，避免刪除或移動時動到其他帳戶的同代號紀錄
    private suspend fun currentAccountTransactions(): List<TransactionItem> =
        repository.transactionsByAccount(settingsRepository.currentAccountIdFlow.first()).first()

    fun deleteStock(symbol: String) {
        viewModelScope.launch {
            val txs = currentAccountTransactions().filter { it.symbol == symbol }
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
            val txs = currentAccountTransactions().filter { symbols.contains(it.symbol) }
            repository.deleteTransactionsByIds(txs.map { it.id })
            exitSelectionMode()
            toastMessage.emit("已刪除 ${symbols.size} 支股票")
        }
    }

    fun moveSelectedStocks(targetAccountId: Long) {
        viewModelScope.launch {
            val symbols = selectedSymbols.toList()
            val txsToMove = currentAccountTransactions().filter { symbols.contains(it.symbol) }
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
    val activeList: List<StockDisplayItem> = emptyList(),
    val closedList: List<StockDisplayItem> = emptyList(),
    val heldSymbols: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val currentAccountId: Long = 1L
)
