package com.example.stock.feature.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.enumClass.DashboardIds
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.domain.GetStockInventoryUseCase
import com.example.stock.core.domain.model.StockPosition
import com.example.stock.core.ui.component.toCurrencyString
import com.example.stock.core.ui.theme.LossColor
import com.example.stock.core.ui.theme.ProfitColor
import com.example.stock.feature.detail.component.FormattedValue
import com.example.stock.feature.detail.component.ProfitDisplay
import com.example.stock.feature.detail.component.StockSummary
import com.example.stock.feature.setting.DashboardSettingItem
import com.example.stock.navigation.AllScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 詳情頁 ViewModel (重構後：引用統一損益算法，確保資料一致)
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val getStockInventoryUseCase: GetStockInventoryUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val symbol: String = savedStateHandle[AllScreens.Companion.ARG_SYMBOL] ?: ""

    // 1. 取得目前選中的帳戶 ID
    val currentAccountId: StateFlow<Long> = settingsRepository.currentAccountIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1L)

    private val _isCumulative = MutableStateFlow(true)
    val isCumulativeFlow = _isCumulative.asStateFlow()
    var isCumulative by mutableStateOf(true)
        private set

    fun toggleProfitMode() {
        isCumulative = !isCumulative
        _isCumulative.value = isCumulative
    }

    var isSelectionMode by mutableStateOf(false)
        private set

    var selectedIds by mutableStateOf(emptySet<Long>())
        private set

    // 所有的相關交易 (用於列表顯示)
    val relatedTransactions = combine(
        repository.transactionsDesc,
        currentAccountId
    ) { list, accountId ->
        if (symbol == "CASH") {
            list.filter { it.accountId == accountId }
        } else {
            list.filter { it.symbol == symbol && it.accountId == accountId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 核心數據流：調用統一的 UseCase
    private val positionState: StateFlow<StockPosition?> = combine(
        relatedTransactions,
        repository.stockPricesFlow,
        settingsRepository.twSettingsFlow,
        settingsRepository.costBasisMethodFlow,
        settingsRepository.includeDividendsFlow,
        repository.allAccounts,
        currentAccountId
    ) { args ->
        val transactions = args[0] as List<TransactionItem>
        val quotes = args[1] as Map<String, com.example.stock.core.data.dataClass.StockQuote>
        val settings = args[2] as SettingsRepository.TwSettings
        val method = args[3] as com.example.stock.core.data.model.CostBasisMethod
        val includeDiv = args[4] as Boolean
        val accounts = args[5] as List<Account>
        val accId = args[6] as Long

        if (symbol == "CASH") {
            // 現金帳戶特殊處理
            val calcResult = getStockInventoryUseCase(transactions, quotes, settings, method, includeDiv)
            val currentAccount = accounts.find { it.id == accId }
            val balance = (currentAccount?.initialBalance ?: 0.0) + calcResult.totalCashFlow
            
            StockPosition(
                symbol = "CASH",
                name = "現金帳戶",
                currentPrice = balance,
                netMarketValue = balance,
                yesterdayNetValue = balance
            )
        } else {
            val calcResult = getStockInventoryUseCase(transactions, quotes, settings, method, includeDiv)
            calcResult.positions[symbol]
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 為了相容現有的 UI Component，將 StockPosition 映射回 StockSummary
    val baseSummary: StateFlow<StockSummary> = positionState.map { pos ->
        pos?.let {
            StockSummary(
                remainingShares = it.shares,
                avgCost = it.avgCost,
                totalBuyAmount = it.totalBuyAmount,
                totalSellAmount = it.totalSellAmount,
                dividendAmount = it.dividendAmount,
                currentPrice = it.currentPrice,
                yesterdayPrice = it.yesterdayPrice,
                netMarketValue = it.netMarketValue,
                yesterdayNetValue = it.yesterdayNetValue,
                buyShares = it.buyShares,
                sellShares = it.sellShares,
                avgBuyAmount = it.avgBuyPrice
            )
        } ?: StockSummary()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StockSummary())

    val cumulativeProfit: StateFlow<ProfitDisplay> = combine(
        positionState,
        settingsRepository.includeDividendsFlow,
        _isCumulative
    ) { pos, includeDiv, isCum ->
        if (pos == null) return@combine ProfitDisplay("0", "0.00%", Color.Black, 0.0)
        
        if (symbol == "CASH") {
            return@combine ProfitDisplay(
                amountText = pos.netMarketValue.toCurrencyString(0),
                percentageText = "帳戶餘額",
                color = Color.Black,
                rawValue = pos.netMarketValue
            )
        }

        // 根據新邏輯計算：(未實現 + [若累積模式則加已實現]) + [若含息則加股息]
        val base = pos.unrealizedProfit + (if (isCum) pos.realizedProfit else 0.0)
        val profit = if (includeDiv) base + pos.dividendAmount else base

        // 修正分母邏輯：累積模式下使用總投入金額 (totalBuyAmount) 作為分母
        val denominator = if (isCum) {
            pos.totalBuyAmount
        } else {
            if (pos.shares > 0.0001) pos.totalCost else pos.totalBuyAmount
        }
        val profitPercent = if (denominator > 0.01) (profit / denominator) * 100 else 0.0

        val color = when {
            profit > 0.01 -> ProfitColor
            profit < -0.01 -> LossColor
            else -> Color.Black
        }

        val prefix = if (profit > 0.01) "+" else ""
        ProfitDisplay(
            amountText = "$prefix${profit.toCurrencyString(0)}",
            percentageText = "$prefix${profitPercent.toCurrencyString(2)}%",
            color = color,
            rawValue = profit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfitDisplay("0", "0.00%", Color.Black, 0.0))

    // 資訊看板內容
    private val defaultSettings = listOf(
        DashboardSettingItem(DashboardIds.AVG_COST, "平均成本"),
        DashboardSettingItem(DashboardIds.AVG_BUY, "平均買價"),
        DashboardSettingItem(DashboardIds.SHARES, "持有股數"),
        DashboardSettingItem(DashboardIds.MARKET_VAL, "總市值"),
        DashboardSettingItem(DashboardIds.TOTAL_COST, "總成本"),
        DashboardSettingItem(DashboardIds.DIVIDEND, "股息收入"),
        DashboardSettingItem(DashboardIds.DAILY_PROFIT, "今日損益"),
        DashboardSettingItem(DashboardIds.UNREALIZED, "未實現損益"),
        DashboardSettingItem(DashboardIds.REALIZED, "已實現損益")
    )

    val dashboardDisplayItems = combine(
        positionState,
        settingsRepository.dashboardSettingsFlow.map { it ?: defaultSettings },
        settingsRepository.includeDividendsFlow,
        _isCumulative
    ) { pos, layout, includeDiv, isCum ->
        if (symbol == "CASH" || pos == null) return@combine emptyList()
        
        layout.filter { it.isVisible }.map { item ->
            // 動態調整標題以符合當前模式
            val displayItem = when (item.id) {
                DashboardIds.UNREALIZED -> item.copy(title = if (isCum) "總損益" else "未實現損益")
                DashboardIds.TOTAL_COST -> item.copy(title = if (isCum) "累積投入" else "庫存成本")
                else -> item
            }

            val result = calculateValue(displayItem, pos, includeDiv, isCum)
            val color = if (item.id in setOf(DashboardIds.DAILY_PROFIT, DashboardIds.UNREALIZED, DashboardIds.REALIZED)) {
                when {
                    result.rawValue > 0.01 -> ProfitColor
                    result.rawValue < -0.01 -> LossColor
                    else -> Color.Black
                }
            } else Color.Black

            Triple(displayItem, result.text, color)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun calculateValue(item: DashboardSettingItem, p: StockPosition, includeDiv: Boolean, isCum: Boolean): FormattedValue {
        val (value, needsPlusSign) = when (item.id) {
            DashboardIds.AVG_COST -> p.avgCost to false
            DashboardIds.AVG_BUY -> p.avgBuyPrice to false
            DashboardIds.SHARES -> p.shares to false
            DashboardIds.MARKET_VAL -> p.marketValue to false
            DashboardIds.TOTAL_COST -> {
                // 若為累積模式，顯示歷史總投入金額；若為庫存模式，顯示當前持股的剩餘成本
                val v = if (isCum) p.totalBuyAmount else p.totalCost
                v to false
            }
            DashboardIds.DIVIDEND -> p.dividendAmount to false
            DashboardIds.DAILY_PROFIT -> p.dailyProfit to true
            DashboardIds.UNREALIZED -> {
                // 這裡的「損益」項目會根據模式切換：
                // 累積模式 = (預估淨市值 + 已實現 + [股息]) - 總投入
                // 庫存模式 = 預估淨市值 - 剩餘持股成本
                val base = p.unrealizedProfit + (if (isCum) p.realizedProfit else 0.0)
                val v = if (includeDiv) base + p.dividendAmount else base
                v to true
            }
            DashboardIds.REALIZED -> p.realizedProfit to true
            else -> 0.0 to false
        }
        val formatted = if (value > 0.01 && needsPlusSign) "+${value.toCurrencyString(0)}" else value.toCurrencyString(0)
        return FormattedValue(formatted, value)
    }

    val stockName = relatedTransactions.map { 
        if (symbol == "CASH") "現金帳戶"
        else it.firstOrNull()?.name ?: symbol 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), if (symbol == "CASH") "現金帳戶" else symbol)

    fun deleteTransaction(t: TransactionItem) = viewModelScope.launch { repository.deleteTransactionById(t.id) }
    
    fun toggleSelection(id: Long) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) isSelectionMode = false
    }

    fun enterSelectionMode(initialSelectedId: Long) {
        isSelectionMode = true
        selectedIds = setOf(initialSelectedId)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    fun deleteSelectedTransactions() {
        viewModelScope.launch {
            repository.deleteTransactionsByIds(selectedIds.toList())
            exitSelectionMode()
        }
    }

    fun moveSelectedTransactions(targetAccountId: Long) {
        viewModelScope.launch {
            val transactionsToMove = relatedTransactions.value.filter { selectedIds.contains(it.id) }
            val updatedTransactions = transactionsToMove.map { it.copy(accountId = targetAccountId) }
            repository.upsertTransactions(updatedTransactions)
            exitSelectionMode()
        }
    }

    val allAccounts: StateFlow<List<Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
