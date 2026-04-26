package com.example.stock.feature.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.enumClass.DashboardIds
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.ui.component.toCurrencyString
import com.example.stock.core.ui.theme.LossColor
import com.example.stock.core.ui.theme.ProfitColor
import com.example.stock.core.data.model.Account
import com.example.stock.feature.detail.component.FormattedValue
import com.example.stock.feature.detail.component.ProfitDisplay
import com.example.stock.feature.detail.component.StockSummary
import com.example.stock.feature.setting.DashboardSettingItem
import com.example.stock.navigation.AllScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val financialCalculator: com.example.stock.core.data.FinancialCalculator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val symbol: String = savedStateHandle[AllScreens.Companion.ARG_SYMBOL] ?: ""

    // 1. 取得目前選中的帳戶 ID
    val currentAccountId: StateFlow<Long> = settingsRepository.currentAccountIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1L)

    var isSelectionMode by mutableStateOf(false)
        private set

    var selectedIds by mutableStateOf(emptySet<Long>())
        private set

    val relatedTransactions = combine(
        repository.transactionsDesc,
        currentAccountId
    ) { list, accountId ->
        if (symbol == "CASH") {
            list.filter { it.accountId == accountId }
        } else {
            list.filter { it.symbol == symbol && it.accountId == accountId }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Companion.WhileSubscribed(5000),
        emptyList()
    )

    val includeDividends: StateFlow<Boolean> = settingsRepository.includeDividendsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val userSettings = settingsRepository.twSettingsFlow // 假設這是你的全域設定 Flow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsRepository.TwSettings()
        )

    private val baseSummary = combine(
        relatedTransactions,
        repository.stockPricesFlow,
        userSettings,
        repository.allAccounts,
        currentAccountId
    ) { transactions, priceMap, settings, accounts, accId ->
        if (symbol == "CASH") {
            val currentAccount = accounts.find { it.id == accId }
            val initialBalance = currentAccount?.initialBalance ?: 0.0
            
            // 計算現金流入 (存款, 出售股票所得, 股息)
            val cashIn = transactions.sumOf { 
                when (it.type) {
                    TransactionType.DEPOSIT -> it.total
                    TransactionType.SELL -> it.total
                    TransactionType.DIVIDEND -> it.dividend
                    else -> 0.0
                }
            }
            
            // 計算現金流出 (提款, 購買股票花費)
            val cashOut = transactions.sumOf {
                when (it.type) {
                    TransactionType.WITHDRAW -> it.total
                    TransactionType.BUY -> it.total
                    else -> 0.0
                }
            }
            
            val balance = initialBalance + cashIn - cashOut
            
            return@combine StockSummary(
                remainingShares = 0.0,
                avgCost = 0.0,
                totalBuyAmount = 0.0,
                totalSellAmount = 0.0,
                dividendAmount = 0.0,
                currentPrice = balance,
                yesterdayPrice = balance,
                netMarketValue = balance,
                yesterdayNetValue = balance,
                buyShares = 0.0,
                sellShares = 0.0,
                avgBuyAmount = 0.0
            )
        }

        val quote = priceMap[symbol]
        val current = quote?.currentPrice ?: 0.0
        val yesterday = current - (quote?.change ?: 0.0)

        val buyTs = transactions.filter { it.type == TransactionType.BUY || it.type == TransactionType.STOCK_DIVIDEND }
        val sellTs = transactions.filter { it.type == TransactionType.SELL }
        val divTs = transactions.filter { it.type == TransactionType.DIVIDEND }

        val bShares = buyTs.sumOf { it.shares.toDouble() }
        val sShares = sellTs.sumOf { it.shares.toDouble() }
        val bAmount = buyTs.sumOf { it.total }
        val sAmount = sellTs.sumOf { it.total }

        val pureBuyTotal = buyTs.sumOf { trans ->
            if (trans.type == TransactionType.BUY) {
                trans.price * trans.shares.toDouble()
            } else {
                0.0
            }
        }

        val currentGross = (bShares - sShares) * current
        val yesterdayGross = (bShares - sShares) * yesterday

        val currentNet = if (settings.showPreDeduct) {
            val estFee = financialCalculator.calculateFee(
                subtotal = currentGross,
                feeRate = settings.feeRate.toDoubleOrNull() ?: 0.1425,
                discount = settings.discount.toDoubleOrNull() ?: 10.0,
                minFee = settings.minFee.toDoubleOrNull() ?: 20.0
            )
            val estTax = financialCalculator.calculateTax(currentGross)
            currentGross - estFee - estTax
        } else {
            currentGross
        }

        val yesterdayNet = if (settings.showPreDeduct) {
            val estFee = financialCalculator.calculateFee(
                subtotal = yesterdayGross,
                feeRate = settings.feeRate.toDoubleOrNull() ?: 0.1425,
                discount = settings.discount.toDoubleOrNull() ?: 10.0,
                minFee = settings.minFee.toDoubleOrNull() ?: 20.0
            )
            val estTax = financialCalculator.calculateTax(yesterdayGross)
            yesterdayGross - estFee - estTax
        } else {
            yesterdayGross
        }
        StockSummary(
            remainingShares = bShares - sShares,
            avgCost = if (bShares > 0) bAmount / bShares else 0.0,
            totalBuyAmount = bAmount,
            totalSellAmount = sAmount,
            dividendAmount = divTs.sumOf { it.dividend },
            currentPrice = current,
            yesterdayPrice = yesterday,
            netMarketValue = currentNet,
            yesterdayNetValue = yesterdayNet,
            buyShares = bShares,
            sellShares = sShares,
            avgBuyAmount = if (bShares > 0) pureBuyTotal / bShares else 0.0
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StockSummary()
    )

    val cumulativeProfit = combine(
        baseSummary,
        includeDividends
    ) { summary, isInclude ->
        if (symbol == "CASH") {
            return@combine ProfitDisplay(
                amountText = summary.netMarketValue.toCurrencyString(0),
                percentageText = "帳戶餘額",
                color = Color.Black,
                rawValue = summary.netMarketValue
            )
        }

        val unrealizedNet = summary.netMarketValue - (summary.remainingShares * summary.avgCost)
        val realized = summary.totalSellAmount - (summary.sellShares * summary.avgCost)
        val totalProfit = if (isInclude) {
            unrealizedNet + realized + summary.dividendAmount
        } else {
            unrealizedNet + realized
        }

        val profitPercent = if (summary.totalBuyAmount > 0) (totalProfit / summary.totalBuyAmount) * 100 else 0.0

        // 4. 決定格式與顏色
        val color = when {
            totalProfit > 0.01 -> ProfitColor
            totalProfit < -0.01 -> LossColor
            else -> Color.Black
        }

        val prefix = if (totalProfit > 0.01) "+" else ""
        val formattedAmount = "$prefix${totalProfit.toCurrencyString(0)}"
        val formattedPercent = "$prefix${profitPercent.toCurrencyString(2)}%"

        ProfitDisplay(formattedAmount, formattedPercent, color, totalProfit)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProfitDisplay("0", "0.00%", Color.Black, 0.0)
    )

    // 預設排序（當 DataStore 還是空的時候）
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


    private val dashboardLayout = settingsRepository.dashboardSettingsFlow.map {
        it ?: defaultSettings
    }

    val dashboardDisplayItems = combine(
        baseSummary,
        dashboardLayout
    ) { summary, layout ->
        if (symbol == "CASH") return@combine emptyList()
        
        // 只顯示勾選為「可見」的項目
        layout.filter { it.isVisible }.map { item ->
            val result = calculateValue(item, summary)

            val profitRelatedIds = setOf(
                DashboardIds.DAILY_PROFIT,
                DashboardIds.UNREALIZED,
                DashboardIds.REALIZED,
                DashboardIds.CUMULATIVE_PROFIT // 如果你有累積損益，也可以一併加進來！
            )

            val color = if (item.id in profitRelatedIds) {
                when {
                    result.rawValue > 0 -> ProfitColor
                    result.rawValue < 0 -> LossColor
                    else -> Color.Black
                }
            } else {
                Color.Black
            }

            // 把結果包成 Triple 或自定義的 UI Model 傳給 Compose
            Triple(item, result.text, color)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // --- 計算邏輯核心 ---
    private fun calculateValue(
        item: DashboardSettingItem,
        s: StockSummary
    ): FormattedValue {

        val (value, needsPlusSign) = when (item.id) {
            DashboardIds.AVG_COST -> s.avgCost to false
            DashboardIds.AVG_BUY -> s.avgBuyAmount to false
            DashboardIds.SHARES -> s.remainingShares to false
            DashboardIds.MARKET_VAL -> s.netMarketValue to false
            DashboardIds.TOTAL_COST -> s.totalBuyAmount to false
            DashboardIds.DIVIDEND -> s.dividendAmount to false
            DashboardIds.DAILY_PROFIT -> (s.netMarketValue - s.yesterdayNetValue) to true
            DashboardIds.UNREALIZED -> (s.netMarketValue - (s.remainingShares * s.avgCost)) to true
            DashboardIds.REALIZED -> (s.totalSellAmount - (s.sellShares * s.avgCost)) to true
            else -> 0.0 to false
        }

        val formattedText = when {
            value > 0 && needsPlusSign -> "+${value.toCurrencyString(0)}"
            else -> value.toCurrencyString(0)
        }

        return FormattedValue(formattedText, value)
    }
    val stockName = relatedTransactions.map { 
        if (symbol == "CASH") "現金帳戶"
        else it.firstOrNull()?.name ?: symbol 
    }
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), if (symbol == "CASH") "現金帳戶" else symbol)

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
            // 呼叫我們剛剛在 Repository 寫好的批次刪除
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}