package com.example.stock.core.domain

import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.domain.model.StockPosition
import javax.inject.Inject

/**
 * 負責計算股票庫存與損益的 UseCase (統一演算法核心)
 */
class GetStockInventoryUseCase @Inject constructor(
    private val financialCalculator: FinancialCalculator
) {

    data class Result(
        val positions: Map<String, StockPosition>,
        val totalRealizedProfit: Double,
        val totalCashFlow: Double, // 帳戶現金流 (不含初始餘額)
        val portfolioUnrealizedProfit: Double,
        val portfolioDailyProfit: Double
    )

    operator fun invoke(
        allTransactions: List<TransactionItem>,
        quotes: Map<String, StockQuote> = emptyMap(),
        settings: SettingsRepository.TwSettings = SettingsRepository.TwSettings(),
        method: CostBasisMethod = CostBasisMethod.AVERAGE_COST,
        includeDividends: Boolean = false
    ): Result {
        // 1. 基礎庫存與已實現損益計算
        val baseData = calculateBaseInventory(allTransactions, method)
        
        var portfolioUnrealized = 0.0
        var portfolioDaily = 0.0

        val positions = baseData.inventoryMap.mapValues { (symbol, data) ->
            val quote = quotes[symbol]
            val currentPrice = quote?.currentPrice ?: 0.0
            val change = quote?.change ?: 0.0
            val yesterdayPrice = currentPrice - change
            
            val shares = data.shares
            val totalCost = data.totalCost

            // 1. 計算市值與昨日總額
            val marketValue = shares * currentPrice
            val yesterdayGrossValue = shares * yesterdayPrice

            // 2. 計算預扣後的淨市值 (供 StockPosition 參考，並連動損益計算)
            val netMarketValue = if (settings.showPreDeduct && shares > 0) {
                val isEtf = financialCalculator.isTaiwanEtf(symbol)
                val estFee = financialCalculator.calculateFee(
                    subtotal = marketValue,
                    feeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble(),
                    discount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble(),
                    minFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                )
                val estTax = financialCalculator.calculateTax(marketValue, isEtf)
                marketValue - estFee - estTax
            } else {
                marketValue
            }

            val yesterdayNetValue = if (settings.showPreDeduct && shares > 0) {
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

            // 3. 損益計算 (若開啟預扣則使用淨值)
            val unrealized = if (shares > 0) netMarketValue - totalCost else 0.0
            val daily = if (shares > 0) netMarketValue - yesterdayNetValue else 0.0
            
            portfolioUnrealized += unrealized
            portfolioDaily += daily

            StockPosition(
                symbol = symbol,
                name = data.name,
                shares = shares,
                // 平均成本計算
                avgCost = if (shares > 0) totalCost / shares else 0.0,
                totalCost = totalCost,
                totalBuyAmount = data.totalBuyAmount,
                totalSellAmount = data.totalSellAmount,
                buyShares = data.buyShares,
                sellShares = data.sellShares,
                dividendAmount = data.dividendAmount,
                // 純買入平均價
                avgBuyPrice = if (data.buyShares > 0) data.pureBuyTotal / data.buyShares else 0.0,
                currentPrice = currentPrice,
                yesterdayPrice = yesterdayPrice,
                marketValue = marketValue,
                netMarketValue = netMarketValue,
                yesterdayNetValue = yesterdayNetValue,
                unrealizedProfit = unrealized,
                realizedProfit = data.realizedProfit, // 這裡僅代表賣出產生的已實現損益
                dailyProfit = daily,
                totalProfit = unrealized + data.realizedProfit + data.dividendAmount // 這是全含的總計 (供參考)
            )
        }

        val finalTotalRealized = positions.values.sumOf { it.realizedProfit }

        return Result(
            positions = positions,
            totalRealizedProfit = finalTotalRealized,
            totalCashFlow = baseData.totalCashFlow,
            portfolioUnrealizedProfit = portfolioUnrealized,
            portfolioDailyProfit = portfolioDaily
        )
    }

    private data class BaseCalculation(
        val inventoryMap: Map<String, SymbolData>,
        val realizedProfit: Double,
        val totalCashDividend: Double,
        val totalCashFlow: Double
    )

    private data class SymbolData(
        val name: String,
        val shares: Double,
        val totalCost: Double,
        val totalBuyAmount: Double,
        val totalSellAmount: Double,
        val buyShares: Double,
        val sellShares: Double,
        val dividendAmount: Double,
        val pureBuyTotal: Double,
        val realizedProfit: Double
    )

    private fun calculateBaseInventory(
        allTransactions: List<TransactionItem>,
        method: CostBasisMethod
    ): BaseCalculation {
        val symbolDataMap = mutableMapOf<String, SymbolState>()
        var totalCashDividend = 0.0
        var totalRealizedProfit = 0.0
        var totalCashFlow = 0.0

        val sortedTransactions = allTransactions.sortedBy { it.date }

        for (trade in sortedTransactions) {
            val state = symbolDataMap.getOrPut(trade.symbol) { SymbolState(trade.name) }
            
            when (trade.type) {
                TransactionType.DEPOSIT -> totalCashFlow += trade.total
                TransactionType.WITHDRAW -> totalCashFlow -= trade.total
                TransactionType.ADJUSTMENT -> totalCashFlow += trade.total
                TransactionType.DIVIDEND -> {
                    // 使用 trade.total (淨額) 而非 trade.dividend (毛額)，以確保扣除匯費後與實際入帳金額一致
                    val netDividend = trade.total
                    state.dividendAmount += netDividend
                    totalCashDividend += netDividend
                    totalCashFlow += netDividend
                }
                TransactionType.BUY -> {
                    val totalPay = (trade.price * trade.shares) + trade.fee
                    state.shares += trade.shares
                    state.totalCostPool += totalPay
                    state.totalBuyAmount += totalPay
                    state.buyShares += trade.shares
                    state.pureBuyTotal += (trade.price * trade.shares)
                    totalCashFlow -= totalPay
                    
                    if (method == CostBasisMethod.FIFO) {
                        state.fifoLots.add(trade.shares.toDouble() to (totalPay / trade.shares))
                    }
                }
                TransactionType.SELL -> {
                    val isEtf = financialCalculator.isTaiwanEtf(trade.symbol)
                    val subtotal = trade.price * trade.shares
                    // 優先使用交易紀錄中儲存的稅金，若為 0 (舊資料) 則依目前公式補算
                    val tax = if (trade.tax > 0) trade.tax else financialCalculator.calculateTax(subtotal, isEtf)
                    val sellNetRevenue = subtotal - trade.fee - tax
                    val actualSoldShares = minOf(state.shares, trade.shares.toDouble())
                    
                    if (actualSoldShares > 0) {
                        val costOfGoodsSold = if (method == CostBasisMethod.AVERAGE_COST) {
                            (state.totalCostPool / state.shares) * actualSoldShares
                        } else {
                            var consumed = 0.0
                            var cost = 0.0
                            while (consumed < actualSoldShares && state.fifoLots.isNotEmpty()) {
                                val (lotShares, lotUnitCost) = state.fifoLots.first()
                                val take = minOf(actualSoldShares - consumed, lotShares)
                                cost += take * lotUnitCost
                                consumed += take
                                if (take >= lotShares) state.fifoLots.removeAt(0)
                                else state.fifoLots[0] = (lotShares - take) to lotUnitCost
                            }
                            cost
                        }
                        
                        val profit = sellNetRevenue - costOfGoodsSold
                        state.realizedProfit += profit
                        totalRealizedProfit += profit
                        
                        state.shares -= actualSoldShares
                        state.totalCostPool -= costOfGoodsSold
                        state.totalSellAmount += sellNetRevenue
                        state.sellShares += actualSoldShares
                        totalCashFlow += sellNetRevenue
                    }
                }
                TransactionType.STOCK_DIVIDEND, TransactionType.SPLIT -> {
                    val oldShares = state.shares
                    state.shares += trade.shares
                    if (method == CostBasisMethod.FIFO && oldShares > 0) {
                        val ratio = state.shares / oldShares
                        for (i in state.fifoLots.indices) {
                            val (lS, lC) = state.fifoLots[i]
                            state.fifoLots[i] = (lS * ratio) to (lC / ratio)
                        }
                    }
                    // 股票股利不增加 pureBuyTotal，因為是無償配股
                    if (trade.type == TransactionType.STOCK_DIVIDEND) {
                        // Keep current pureBuyTotal
                    } else {
                        // 分割通常也不涉及現金支出
                    }
                }
                TransactionType.CAPITAL_REDUCTION -> {
                    val oldShares = state.shares
                    state.shares += trade.shares // trade.shares 為負數，代表減少股數
                    val cashReturned = trade.total // 退還的現金總額
                    
                    // 核心修正：若退還現金 > 目前成本池，超出的部分應視為「已實現損益」
                    val excess = cashReturned - state.totalCostPool
                    if (excess > 0) {
                        state.realizedProfit += excess
                        state.totalCostPool = 0.0
                    } else {
                        state.totalCostPool -= cashReturned
                    }

                    totalCashFlow += cashReturned
                    
                    if (method == CostBasisMethod.FIFO && oldShares > 0) {
                        val ratio = state.shares / oldShares
                        val cashPerOldShare = if (oldShares != 0.0) cashReturned / oldShares else 0.0
                        for (i in state.fifoLots.indices) {
                            val (lS, lC) = state.fifoLots[i]
                            val newS = lS * ratio
                            val costReduction = lS * cashPerOldShare
                            state.fifoLots[i] = newS to (if (newS > 0) maxOf(0.0, (lS * lC - costReduction) / newS) else 0.0)
                        }
                    }
                }
            }
        }

        return BaseCalculation(
            inventoryMap = symbolDataMap.mapValues { (_, s) ->
                SymbolData(s.name, s.shares, s.totalCostPool, s.totalBuyAmount, s.totalSellAmount, s.buyShares, s.sellShares, s.dividendAmount, s.pureBuyTotal, s.realizedProfit)
            },
            realizedProfit = totalRealizedProfit,
            totalCashDividend = totalCashDividend,
            totalCashFlow = totalCashFlow
        )
    }

    private class SymbolState(val name: String) {
        var shares = 0.0
        var totalCostPool = 0.0
        var totalBuyAmount = 0.0
        var totalSellAmount = 0.0
        var buyShares = 0.0
        var sellShares = 0.0
        var dividendAmount = 0.0
        var pureBuyTotal = 0.0
        var realizedProfit = 0.0
        val fifoLots = mutableListOf<Pair<Double, Double>>()
    }
}
