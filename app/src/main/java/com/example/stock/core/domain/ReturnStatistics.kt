package com.example.stock.core.domain

import com.example.stock.core.domain.GetStockInventoryUseCase.BaseCalculation
import com.example.stock.core.domain.model.StockPosition
import kotlinx.datetime.LocalDate

/**
 * 報酬率統計：整體與各股票的 XIRR
 * 現金流為股票的買進 / 賣出 / 股息 / 減資退款；持股中的股票以今天的市值當作期末取回
 * 入金、出金、調整等純現金交易不列入，因此不受現金管理設定影響
 */
object ReturnStatistics {

    data class StockReturn(
        val symbol: String,
        val name: String,
        val isActive: Boolean,
        val invested: Double,     // 累計買進金額 (含手續費)
        val received: Double,     // 累計取回 (賣出淨額 + 股息 + 減資退款)
        val currentValue: Double, // 目前市值 (已清倉為 0)
        val firstDate: LocalDate,
        val xirr: Double?         // 0.1 代表 10%
    ) {
        val profit: Double get() = received + currentValue - invested
        val profitPercent: Double get() = if (invested > 0.01) profit / invested * 100 else 0.0
    }

    data class PortfolioReturn(
        val invested: Double,
        val received: Double,
        val currentValue: Double,
        val firstDate: LocalDate,
        val xirr: Double?,
        val stocks: List<StockReturn> // 持股中在前，各自依 XIRR 由高到低
    ) {
        val profit: Double get() = received + currentValue - invested
        val profitPercent: Double get() = if (invested > 0.01) profit / invested * 100 else 0.0
    }

    /** 沒有任何股票交易時回傳 null */
    fun calculate(
        base: BaseCalculation,
        positions: Map<String, StockPosition>,
        today: LocalDate
    ): PortfolioReturn? {
        val flows = base.investmentFlows
        if (flows.isEmpty()) return null

        val stocks = flows.groupBy { it.symbol }.map { (symbol, symbolFlows) ->
            val position = positions[symbol]
            val currentValue = position?.takeIf { it.shares > 0 }?.marketValue ?: 0.0
            val cashFlows = symbolFlows.map { Xirr.CashFlow(it.date, it.amount) } +
                listOfNotNull(currentValue.takeIf { it > 0 }?.let { Xirr.CashFlow(today, it) })
            StockReturn(
                symbol = symbol,
                name = position?.name ?: symbol,
                isActive = currentValue > 0,
                invested = -symbolFlows.filter { it.amount < 0 }.sumOf { it.amount },
                received = symbolFlows.filter { it.amount > 0 }.sumOf { it.amount },
                currentValue = currentValue,
                firstDate = symbolFlows.minOf { it.date },
                xirr = Xirr.calculate(cashFlows)
            )
        }.sortedWith(
            compareByDescending<StockReturn> { it.isActive }
                .thenByDescending { it.xirr ?: Double.NEGATIVE_INFINITY }
        )

        val totalValue = stocks.sumOf { it.currentValue }
        val portfolioFlows = flows.map { Xirr.CashFlow(it.date, it.amount) } +
            listOfNotNull(totalValue.takeIf { it > 0 }?.let { Xirr.CashFlow(today, it) })

        return PortfolioReturn(
            invested = stocks.sumOf { it.invested },
            received = stocks.sumOf { it.received },
            currentValue = totalValue,
            firstDate = flows.minOf { it.date },
            xirr = Xirr.calculate(portfolioFlows),
            stocks = stocks
        )
    }
}
