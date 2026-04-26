package com.example.stock.core.domain

import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.feature.home.component.StockInventory
import javax.inject.Inject

/**
 * 負責計算股票庫存與損益的 UseCase
 */
class GetStockInventoryUseCase @Inject constructor() {

    data class Result(
        val inventoryMap: Map<String, StockInventory>,
        val realizedProfit: Double,
        val totalCashFlow: Double // 帳戶現金流 (入金 - 出金)
    )

    operator fun invoke(
        allTransactions: List<TransactionItem>,
        method: CostBasisMethod,
        includeDividends: Boolean
    ): Result {
        val inventoryMap = mutableMapOf<String, Pair<Double, Double>>() // symbol -> (shares, totalCost)
        var totalCashDividend = 0.0
        var totalRealizedProfit = 0.0
        var totalCashFlow = 0.0

        // 依日期排序，確保計算邏輯正確
        val sortedTransactions = allTransactions.sortedBy { it.date }

        if (method == CostBasisMethod.AVERAGE_COST) {
            // --- 平均成本法與庫存計算 ---
            for (trade in sortedTransactions) {
                val current = inventoryMap.getOrDefault(trade.symbol, 0.0 to 0.0)
                var currentShares = current.first
                var totalCostPool = current.second

                when (trade.type) {
                    TransactionType.DEPOSIT -> {
                        totalCashFlow += trade.total
                    }
                    TransactionType.WITHDRAW -> {
                        totalCashFlow -= trade.total
                    }
                    TransactionType.ADJUSTMENT -> {
                        totalCashFlow += trade.total
                    }
                    TransactionType.BUY -> {
                        val totalPay = (trade.price * trade.shares * trade.multiplier) + trade.fee
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to (totalCostPool + totalPay)
                        totalCashFlow -= totalPay
                    }
                    TransactionType.STOCK_DIVIDEND -> {
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to totalCostPool
                    }
                    TransactionType.SPLIT -> {
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to totalCostPool
                    }
                    TransactionType.CAPITAL_REDUCTION -> {
                        // 減資：減少股數，可能退還現金。退還的現金視為成本收回。
                        val newShares = currentShares + trade.shares
                        val newCost = totalCostPool - trade.total
                        inventoryMap[trade.symbol] = newShares to newCost
                        totalCashFlow += trade.total
                    }
                    TransactionType.DIVIDEND -> {
                        totalCashDividend += trade.dividend
                        totalCashFlow += trade.dividend
                    }
                    TransactionType.SELL -> {
                        if (currentShares > 0) {
                            val averageCost = totalCostPool / currentShares
                            val actualSoldShares = minOf(currentShares, trade.shares.toDouble())
                            
                            // 損益計算：(賣出金額 - 賣出交易稅費) - 買入成本
                            val sellNetRevenue = (trade.price * trade.shares * trade.multiplier) - trade.fee
                            val costOfGoodsSold = averageCost * actualSoldShares
                            totalRealizedProfit += (sellNetRevenue - costOfGoodsSold)

                            // 更新剩餘庫存
                            inventoryMap[trade.symbol] = (currentShares - actualSoldShares) to (totalCostPool - costOfGoodsSold)
                            
                            // 現金增加：賣出淨額
                            totalCashFlow += sellNetRevenue
                        }
                    }
                }
            }
        } else {
            // --- FIFO 先進先出邏輯 ---
            val buyLotsMap = mutableMapOf<String, MutableList<Pair<Double, Double>>>() // symbol -> list of (shares, unitCost)

            for (trade in sortedTransactions) {
                val current = inventoryMap.getOrDefault(trade.symbol, 0.0 to 0.0)
                var currentShares = current.first
                var totalCostPool = current.second

                when (trade.type) {
                    TransactionType.DEPOSIT -> {
                        totalCashFlow += trade.total
                    }
                    TransactionType.WITHDRAW -> {
                        totalCashFlow -= trade.total
                    }
                    TransactionType.ADJUSTMENT -> {
                        totalCashFlow += trade.total
                    }
                    TransactionType.BUY -> {
                        val totalPay = (trade.price * trade.shares * trade.multiplier) + trade.fee
                        val unitCost = totalPay / trade.shares
                        
                        val lots = buyLotsMap.getOrPut(trade.symbol) { mutableListOf() }
                        lots.add(trade.shares.toDouble() to unitCost)
                        
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to (totalCostPool + totalPay)
                    }
                    TransactionType.STOCK_DIVIDEND -> {
                        val lots = buyLotsMap.getOrPut(trade.symbol) { mutableListOf() }
                        lots.add(trade.shares.toDouble() to 0.0)
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to totalCostPool
                    }
                    TransactionType.SPLIT -> {
                        // SPLIT 在 FIFO 中：按比例調整現有的所有 Lots
                        val lots = buyLotsMap[trade.symbol] ?: mutableListOf()
                        if (currentShares > 0) {
                            val ratio = (currentShares + trade.shares) / currentShares
                            for (i in lots.indices) {
                                val (lShares, lUnitCost) = lots[i]
                                lots[i] = (lShares * ratio) to (lUnitCost / ratio)
                            }
                        }
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to totalCostPool
                    }
                    TransactionType.CAPITAL_REDUCTION -> {
                        // 減資在 FIFO 中：按比例減少股數，並從每個 Lot 中扣除收回的成本
                        val lots = buyLotsMap[trade.symbol] ?: mutableListOf()
                        if (currentShares > 0) {
                            val ratio = (currentShares + trade.shares) / currentShares
                            val cashPerShare = if (trade.shares != 0) trade.total / Math.abs(trade.shares.toDouble()) else 0.0
                            
                            for (i in lots.indices) {
                                val (lShares, lUnitCost) = lots[i]
                                val reduction = lShares * (1.0 - ratio)
                                val cashReceivedForLot = reduction * cashPerShare // 這裡簡化處理，假設按比例分配退款
                                val newShares = lShares * ratio
                                val newUnitCost = if (newShares > 0) (lShares * lUnitCost - cashReceivedForLot) / newShares else 0.0
                                lots[i] = newShares to newUnitCost
                            }
                        }
                        totalCashFlow += trade.total
                        inventoryMap[trade.symbol] = (currentShares + trade.shares) to (totalCostPool - trade.total)
                    }
                    TransactionType.DIVIDEND -> {
                        totalCashDividend += trade.dividend
                        totalCashFlow += trade.dividend
                    }
                    TransactionType.SELL -> {
                        var sharesToSell = trade.shares.toDouble()
                        val sellTotalGross = (trade.price * trade.shares * trade.multiplier)
                        var buyCostOfSoldShares = 0.0
                        
                        val lots = buyLotsMap[trade.symbol] ?: mutableListOf()
                        
                        while (sharesToSell > 0 && lots.isNotEmpty()) {
                            val (lotShares, lotUnitCost) = lots.first()
                            val consumedShares = minOf(sharesToSell, lotShares)
                            buyCostOfSoldShares += consumedShares * lotUnitCost
                            sharesToSell -= consumedShares
                            
                            if (consumedShares >= lotShares) {
                                lots.removeAt(0)
                            } else {
                                lots[0] = (lotShares - consumedShares) to lotUnitCost
                            }
                        }
                        
                        totalRealizedProfit += (sellTotalGross - trade.fee - buyCostOfSoldShares)
                        
                        // 更新庫存狀態
                        val actualSold = minOf(currentShares, trade.shares.toDouble())
                        val reductionRatio = if (currentShares > 0) actualSold / currentShares else 0.0
                        inventoryMap[trade.symbol] = (currentShares - actualSold) to (totalCostPool * (1.0 - reductionRatio))
                    }
                }
            }
        }

        val finalRealizedProfit = if (includeDividends) totalRealizedProfit + totalCashDividend else totalRealizedProfit
        
        return Result(
            inventoryMap = inventoryMap.mapValues { StockInventory(it.value.first, it.value.second) },
            realizedProfit = finalRealizedProfit,
            totalCashFlow = totalCashFlow
        )
    }
}
