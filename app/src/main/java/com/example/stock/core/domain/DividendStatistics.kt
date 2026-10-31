package com.example.stock.core.domain

import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType

/**
 * 股息統計：依年 / 月 / 股票彙總實收的現金股息
 * 金額使用 total (扣除匯費後的淨額)，與首頁損益計算一致
 */
object DividendStatistics {

    data class StockDividend(
        val symbol: String,
        val name: String,
        val amount: Double,
        val count: Int
    )

    data class YearDividend(
        val year: Int,
        val total: Double,
        val monthly: List<Double>, // 固定 12 筆，index 0 為 1 月
        val byStock: List<StockDividend> // 依金額由大到小
    )

    /** 回傳有股息的年份，由新到舊 */
    fun calculate(transactions: List<TransactionItem>): List<YearDividend> =
        transactions
            .filter { it.type == TransactionType.DIVIDEND }
            .groupBy { it.date.year }
            .map { (year, txs) ->
                val monthly = MutableList(12) { 0.0 }
                txs.forEach { monthly[it.date.monthNumber - 1] += it.total }

                val byStock = txs.groupBy { it.symbol }
                    .map { (symbol, list) ->
                        StockDividend(
                            symbol = symbol,
                            name = list.first().name,
                            amount = list.sumOf { it.total },
                            count = list.size
                        )
                    }
                    .sortedByDescending { it.amount }

                YearDividend(year, txs.sumOf { it.total }, monthly, byStock)
            }
            .sortedByDescending { it.year }
}
