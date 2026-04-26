package com.example.stock.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.LicenseManager
import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.data.source.SearchRegion
import com.example.stock.core.data.source.StockFetcher
import com.example.stock.core.domain.GetStockInventoryUseCase
import com.example.stock.feature.home.component.PortfolioSummary
import com.example.stock.feature.home.component.StockDisplayItem
import com.example.stock.feature.home.component.StockInventory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val stockFetcher: StockFetcher,
    private val financialCalculator: FinancialCalculator,
    private val getStockInventoryUseCase: GetStockInventoryUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var lastUpdateTime = 0L

    // 1. 取得所有帳戶清單
    val allAccounts: StateFlow<List<com.example.stock.core.data.model.Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. 取得目前選中的帳戶 ID
    val currentAccountId: StateFlow<Long> = settingsRepository.currentAccountIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1L)

    // 3. 根據帳戶 ID 動態過濾交易紀錄
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionItem>> = currentAccountId
        .flatMapLatest { accountId ->
            repository.transactionsDesc.map { list ->
                list.filter { it.accountId == accountId }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val _stockQuotes = MutableStateFlow<Map<String, StockQuote>>(emptyMap())
    val stockQuotes = _stockQuotes.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _searchRegion = MutableStateFlow(SearchRegion.TW)
    val searchRegion = _searchRegion.asStateFlow()

    var isCumulative by mutableStateOf(true)

    // 保存當前選中的 Tab 索引 (0: 持有中, 1: 已清倉)
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
    }

    val lastUpdateTimestamp: StateFlow<Long> = settingsRepository.lastUpdateTimestampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val currentAccount: StateFlow<Account?> = combine(
        allAccounts,
        currentAccountId
    ) { accounts, id ->
        accounts.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isCashManagementEnabled: StateFlow<Boolean> = currentAccount
        .map { it?.isCashManagementEnabled ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val includeDividends: StateFlow<Boolean> = settingsRepository.includeDividendsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 核心數據流：透過 UseCase 統一計算庫存與損益
    private val inventoryResult: StateFlow<GetStockInventoryUseCase.Result?> = combine(
        transactions,
        settingsRepository.costBasisMethodFlow,
        includeDividends
    ) { list: List<TransactionItem>, method: CostBasisMethod, includeDiv: Boolean ->
        if (list.isEmpty()) null
        else getStockInventoryUseCase(list, method, includeDiv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val inventory: StateFlow<Map<String, StockInventory>> = inventoryResult
        .map { it?.inventoryMap ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val totalAssetsValue: Flow<Double> = combine(
        inventoryResult,
        _stockQuotes,
        settingsRepository.twSettingsFlow,
        allAccounts,
        currentAccountId,
        isCashManagementEnabled
    ) { args: Array<Any?> ->
        val result = args[0] as? GetStockInventoryUseCase.Result
        val prices = args[1] as Map<String, StockQuote>
        val settings = args[2] as SettingsRepository.TwSettings
        val accounts = args[3] as List<com.example.stock.core.data.model.Account>
        val accId = args[4] as Long
        val cashEnabled = args[5] as Boolean
        var total = 0.0
        
        // 1. 加上選定帳戶的初始餘額與現金流 (入金 - 出金)
        if (cashEnabled) {
            val currentAccount = accounts.find { it.id == accId }
            val initialBalance = currentAccount?.initialBalance ?: 0.0
            val cashFlow = result?.totalCashFlow ?: 0.0
            total += (initialBalance + cashFlow)
        }

        // 2. 加上股票市值
        val currentInventory = result?.inventoryMap ?: emptyMap()
        for ((symbol, stockInfo) in currentInventory) {
            if (stockInfo.shares > 0) {
                val currentPrice = prices[symbol]?.currentPrice ?: 0.0
                val marketValue = stockInfo.shares * currentPrice

                if (settings.showPreDeduct) {
                    val isEtf = financialCalculator.isTaiwanEtf(symbol)
                    val estSellFee = financialCalculator.calculateFee(
                        subtotal = marketValue,
                        feeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble(),
                        discount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble(),
                        minFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                    )
                    val estTax = financialCalculator.calculateTax(marketValue, isEtf)
                    total += (marketValue - estSellFee - estTax)
                } else {
                    total += marketValue
                }
            }
        }
        total
    }

    val totalAssets: StateFlow<Double> = totalAssetsValue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cashBalance: StateFlow<Double> = combine(
        inventoryResult,
        allAccounts,
        currentAccountId,
        isCashManagementEnabled
    ) { args: Array<Any?> ->
        val result = args[0] as? GetStockInventoryUseCase.Result
        val accounts = args[1] as List<com.example.stock.core.data.model.Account>
        val accId = args[2] as Long
        val cashEnabled = args[3] as Boolean

        if (!cashEnabled) return@combine 0.0
        val currentAccount = accounts.find { it.id == accId }
        val initialBalance = currentAccount?.initialBalance ?: 0.0
        val cashFlow = result?.totalCashFlow ?: 0.0
        initialBalance + cashFlow
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val portfolioSummary: StateFlow<PortfolioSummary> = combine(
        inventoryResult,
        _stockQuotes,
        settingsRepository.twSettingsFlow,
        allAccounts,
        currentAccountId,
        isCashManagementEnabled
    ) { args: Array<Any?> ->
        val result = args[0] as? GetStockInventoryUseCase.Result
        val prices = args[1] as Map<String, StockQuote>
        val settings = args[2] as SettingsRepository.TwSettings
        val accounts = args[3] as List<com.example.stock.core.data.model.Account>
        val accId = args[4] as Long
        val cashEnabled = args[5] as Boolean

        if (result == null) {
            val currentAccount = accounts.find { it.id == accId }
            val initialBalance = currentAccount?.initialBalance ?: 0.0
            val initial = if (cashEnabled) initialBalance else 0.0
            return@combine PortfolioSummary(totalAssets = initial, cashBalance = initial)
        }

        val currentAccount = accounts.find { it.id == accId }
        val initialBalance = currentAccount?.initialBalance ?: 0.0
        val cashFlow = result.totalCashFlow
        val currentCashBalance = if (cashEnabled) initialBalance + cashFlow else 0.0

        val currentInventory = result.inventoryMap
        val realizedProfit = result.realizedProfit
        
        var totalMarketValue = 0.0
        var totalUnrealizedProfit = 0.0
        var totalDailyProfit = 0.0

        currentInventory.filter { it.value.shares > 0 }.forEach { (symbol, data) ->
            val quote = prices[symbol]
            val currentPrice = quote?.currentPrice ?: 0.0
            val priceChange = quote?.change ?: 0.0
            val shares = data.shares

            val todayGrossValue = shares * currentPrice
            val yesterdayGrossValue = shares * (currentPrice - priceChange)

            val todayNetValue = if (settings.showPreDeduct) {
                val isEtf = financialCalculator.isTaiwanEtf(symbol)
                val estFee = financialCalculator.calculateFee(
                    subtotal = todayGrossValue,
                    feeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble(),
                    discount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble(),
                    minFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                )
                val estTax = financialCalculator.calculateTax(todayGrossValue, isEtf)
                todayGrossValue - estFee - estTax
            } else {
                todayGrossValue
            }

            val yesterdayNetValue = if (settings.showPreDeduct) {
                val isEtf = financialCalculator.isTaiwanEtf(symbol)
                val estFee = financialCalculator.calculateFee(
                    subtotal = yesterdayGrossValue,
                    feeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble(),
                    discount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble(),
                    minFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                )
                val estTax = financialCalculator.calculateTax(yesterdayGrossValue, isEtf)
                yesterdayGrossValue - estFee - estTax
            } else {
                yesterdayGrossValue
            }

            totalMarketValue += todayNetValue
            totalUnrealizedProfit += (todayNetValue - data.totalCost)
            totalDailyProfit += (todayNetValue - yesterdayNetValue)
        }

        val yesterdayTotalNetValue = totalMarketValue - totalDailyProfit
        val totalAssetsValueInFlow = totalMarketValue + currentCashBalance

        PortfolioSummary(
            totalAssets = totalAssetsValueInFlow,
            cashBalance = currentCashBalance,
            unrealizedProfit = totalUnrealizedProfit,
            cumulativeProfit = totalUnrealizedProfit + realizedProfit,
            dailyProfit = totalDailyProfit,
            dailyRate = if (yesterdayTotalNetValue > 0) (totalDailyProfit / yesterdayTotalNetValue) * 100 else 0.0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioSummary())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val activeStockList: StateFlow<List<StockDisplayItem>> = combine(
        inventory,
        _stockQuotes,
        settingsRepository.twSettingsFlow,
        transactions,
        cashBalance,
        allAccounts,
        currentAccountId,
        isCashManagementEnabled,
        _searchQuery
    ) { args: Array<Any?> ->
        val currentInventory = args[0] as Map<String, StockInventory>
        val prices = args[1] as Map<String, StockQuote>
        val settings = args[2] as SettingsRepository.TwSettings
        val txList = args[3] as List<TransactionItem>
        val currentCash = args[4] as Double
        val accounts = args[5] as List<com.example.stock.core.data.model.Account>
        val accId = args[6] as Long
        val cashEnabled = args[7] as Boolean
        val query = args[8] as String

        val stockList = currentInventory.filter { it.value.shares > 0 }.map { (symbol, data) ->
            val quote = prices[symbol]
            val currentPrice = quote?.currentPrice ?: 0.0
            val marketValue = data.shares * currentPrice

            val netMarketValue = if (settings.showPreDeduct) {
                val isEtf = financialCalculator.isTaiwanEtf(symbol)
                val estFee = financialCalculator.calculateFee(
                    subtotal = marketValue,
                    feeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble(),
                    discount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble(),
                    minFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                )
                val estTax = financialCalculator.calculateTax(marketValue, isEtf)
                marketValue - estFee - estTax
            } else marketValue

            val profit = netMarketValue - data.totalCost
            val name = txList.find { it.symbol == symbol }?.name ?: symbol

            StockDisplayItem(
                symbol = symbol,
                name = name,
                shares = data.shares,
                currentPrice = currentPrice,
                change = quote?.change ?: 0.0,
                changePercent = quote?.changePercent ?: 0.0,
                avgCost = data.totalCost / data.shares,
                profit = profit,
                profitPercent = if (data.totalCost > 0) (profit / data.totalCost) * 100 else 0.0,
                marketValue = marketValue
            )
        }.filter { 
            it.symbol.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        }
        
        // 加上虛擬的現金項目
        val finalStockList = if (cashEnabled) {
            val cashItem = StockDisplayItem(
                symbol = "CASH",
                name = "現金",
                shares = 0.0,
                currentPrice = 0.0,
                change = 0.0,
                changePercent = 0.0,
                avgCost = 0.0,
                profit = currentCash,
                profitPercent = 0.0,
                marketValue = currentCash,
                subtitle = accounts.find { it.id == accId }?.currency
            )
            listOf(cashItem) + stockList
        } else {
            stockList
        }
        
        finalStockList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val closedStockList: StateFlow<List<StockDisplayItem>> = combine(
        inventoryResult,
        _stockQuotes,
        transactions,
        settingsRepository.costBasisMethodFlow,
        _searchQuery
    ) { args: Array<Any?> ->
        val result = args[0] as? GetStockInventoryUseCase.Result
        val prices = args[1] as Map<String, StockQuote>
        val txList = args[2] as List<TransactionItem>
        val method = args[3] as CostBasisMethod
        val query = args[4] as String

        if (result == null) return@combine emptyList()
        
        // 取得當前的 includeDividends 設定 (從 Flow 中讀取最新值)
        val isIncludeDiv = includeDividends.value

        result.inventoryMap.filter { it.value.shares <= 0 }.mapNotNull { (symbol, _) ->
            val symbolTx = txList.filter { it.symbol == symbol }
            if (symbolTx.isEmpty()) return@mapNotNull null

            val individualResult = getStockInventoryUseCase(symbolTx, method, isIncludeDiv)
            val name = symbolTx.first().name

            if (!symbol.contains(query, ignoreCase = true) && !name.contains(query, ignoreCase = true)) {
                return@mapNotNull null
            }

            StockDisplayItem(
                symbol = symbol,
                name = name,
                shares = 0.0,
                currentPrice = prices[symbol]?.currentPrice ?: 0.0,
                change = prices[symbol]?.change ?: 0.0,
                changePercent = prices[symbol]?.changePercent ?: 0.0,
                avgCost = 0.0,
                profit = individualResult.realizedProfit,
                profitPercent = 0.0,
                marketValue = 0.0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assetDistribution = combine(inventory, stockQuotes) { inv, quotes ->
        inv.mapValues { (symbol, item) ->
            val price = quotes[symbol]?.currentPrice ?: 0.0
            item.shares * price
        }.filterValues { it > 0.0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val assetHistory: StateFlow<List<Pair<Long, Double>>> = MutableStateFlow(emptyList<Pair<Long, Double>>()).asStateFlow()

    init {
        viewModelScope.launch {
            val cachedPrices = repository.getPriceCache()
            if (cachedPrices.isNotEmpty()) {
                _stockQuotes.value = cachedPrices
            }
            launch(Dispatchers.IO) {
                repository.preloadData()
            }
            startAutoUpdateCycle()
        }
    }

    fun deleteTransaction(item: TransactionItem) {
        viewModelScope.launch { repository.deleteTransactionsByIds(listOf(item.id)) }
    }

    fun setCurrentAccount(id: Long) {
        viewModelScope.launch { settingsRepository.setCurrentAccountId(id) }
    }

    fun toggleIncludeDividends(include: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludeDividends(include) }
    }

    fun updateAllPrices() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < MANUAL_COOLDOWN) {
            viewModelScope.launch {
                val waitSeconds = (MANUAL_COOLDOWN - (currentTime - lastUpdateTime)) / 1000
                _toastMessage.emit("更新太頻繁了，請等 $waitSeconds 秒後再試⏳")
            }
            return
        }
        lastUpdateTime = currentTime
        updateAllPrices(isAuto = false)
    }

    private fun updateAllPrices(isAuto: Boolean) {
        if (_isUpdating.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isUpdating.value = true
            if (!isAuto) _toastMessage.emit("正在更新股價...")

            val symbols = transactions.value.map { it.symbol }.distinct()
            val newStockQuotes = _stockQuotes.value.toMutableMap()
            var successCount = 0

            for (symbol in symbols) {
                try {
                    val detail = stockFetcher.fetchStockDetail(symbol)
                    if (detail != null) {
                        newStockQuotes[symbol] = StockQuote(detail.currentPrice, detail.change, detail.changePercent)
                        successCount++
                    }
                    delay(1500)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _stockQuotes.value = newStockQuotes
            repository.savePriceCache(newStockQuotes)
            
            // 更新成功，紀錄時間戳
            if (successCount > 0) {
                settingsRepository.setLastUpdateTimestamp(System.currentTimeMillis())
            }

            _isUpdating.value = false
            if (!isAuto) _toastMessage.emit("更新完成 (成功: $successCount / ${symbols.size})")
        }
    }

    private fun startAutoUpdateCycle() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(3000)
            while (true) {
                updateAllPrices(isAuto = true)
                delay(AUTO_UPDATE_INTERVAL)
            }
        }
    }

    fun verifyAndUnlock(inputCode: String): Boolean {
        val isValid = LicenseManager.isValidProductKey(inputCode)
        if (isValid) {
            viewModelScope.launch { settingsRepository.unlockPremium() }
            return true
        }
        return false
    }

    fun deleteTransactionsBySymbol(symbol: String) {
        viewModelScope.launch {
            val toDelete = transactions.value.filter { it.symbol == symbol }
            repository.deleteTransactionsByIds(toDelete.map { it.id })
            _toastMessage.emit("已刪除 $symbol 的所有紀錄")
        }
    }

    var isSelectionMode by mutableStateOf(false)
    var selectedSymbols by mutableStateOf(setOf<String>())

    fun toggleSelection(symbol: String) {
        selectedSymbols = if (selectedSymbols.contains(symbol)) {
            selectedSymbols - symbol
        } else {
            selectedSymbols + symbol
        }
        if (selectedSymbols.isEmpty()) isSelectionMode = false
    }

    fun enterSelectionMode(symbol: String) {
        isSelectionMode = true
        selectedSymbols = setOf(symbol)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedSymbols = emptySet()
    }

    fun deleteSelectedStocks() {
        viewModelScope.launch {
            val toDelete = transactions.value.filter { selectedSymbols.contains(it.symbol) }
            
            // 如果選中 CASH，我們可能不希望一次刪光所有入出金紀錄（或者希望，這取決於設計）
            // 但目前的設計是刪除所有該 symbol 的交易
            repository.deleteTransactionsByIds(toDelete.map { it.id })
            
            exitSelectionMode()
            _toastMessage.emit("已刪除選中的紀錄")
        }
    }

    fun moveSelectedStocks(targetAccountId: Long) {
        viewModelScope.launch {
            val toMove = transactions.value.filter { selectedSymbols.contains(it.symbol) }
            val updated = toMove.map { it.copy(accountId = targetAccountId) }
            repository.upsertTransactions(updated)
            exitSelectionMode()
            _toastMessage.emit("已將選中的股票移至新帳戶")
        }
    }

    companion object {
        private const val AUTO_UPDATE_INTERVAL = 5 * 60 * 1000L
        private const val MANUAL_COOLDOWN = 30 * 1000L
    }
}
