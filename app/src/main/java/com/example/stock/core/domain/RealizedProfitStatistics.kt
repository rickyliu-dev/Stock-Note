package com.example.stock.core.domain

import com.example.stock.core.domain.GetStockInventoryUseCase.RealizedEvent

/**
 * 年度已實現損益：依年 / 月 / 股票彙總賣出 (及減資超額退款) 產生的已實現損益
 * 成本依目前設定的成本計算方式 (平均成本 / 先進先出)，由 GetStockInventoryUseCase 算出
 */
object RealizedProfitStatistics {

    data class StockRealized(
        val symbol: String,
        val name: String,
        val profit: Double,
        val proceeds: Double,
        val cost: Double,
        val count: Int
    ) {
        val profitPercent: Double get() = if (cost > 0.01) profit / cost * 100 else 0.0
    }

    data class YearRealized(
        val year: Int,
        val total: Double,
        val gain: Double, // 獲利筆數的合計
        val loss: Double, // 虧損筆數的合計 (負數)
        val tradeCount: Int,
        val winCount: Int,
        val monthly: List<Double>, // 固定 12 筆，index 0 為 1 月
        val byStock: List<StockRealized> // 依損益由大到小
    )

    /** 回傳有已實現損益的年份，由新到舊 */
    fun calculate(events: List<RealizedEvent>): List<YearRealized> =
        events
            .groupBy { it.date.year }
            .map { (year, list) ->
                val monthly = MutableList(12) { 0.0 }
                list.forEach { monthly[it.date.monthNumber - 1] += it.profit }

                val byStock = list.groupBy { it.symbol }
                    .map { (symbol, items) ->
                        StockRealized(
                            symbol = symbol,
                            name = items.first().name,
                            profit = items.sumOf { it.profit },
                            proceeds = items.sumOf { it.proceeds },
                            cost = items.sumOf { it.cost },
                            count = items.size
                        )
                    }
                    .sortedByDescending { it.profit }

                YearRealized(
                    year = year,
                    total = list.sumOf { it.profit },
                    gain = list.filter { it.profit > 0 }.sumOf { it.profit },
                    loss = list.filter { it.profit < 0 }.sumOf { it.profit },
                    tradeCount = list.size,
                    winCount = list.count { it.profit > 0 },
                    monthly = monthly,
                    byStock = byStock
                )
            }
            .sortedByDescending { it.year }
}
