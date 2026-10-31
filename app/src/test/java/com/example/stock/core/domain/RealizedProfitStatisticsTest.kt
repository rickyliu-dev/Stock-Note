package com.example.stock.core.domain

import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 透過 GetStockInventoryUseCase.calculateBase 產生已實現事件，再驗證年度彙總
 */
class RealizedProfitStatisticsTest {

    private val calculator = mockk<FinancialCalculator>()
    private lateinit var useCase: GetStockInventoryUseCase

    @Before
    fun setUp() {
        every { calculator.isTaiwanEtf(any()) } returns false
        every { calculator.calculateTax(any(), any()) } returns 0.0
        every { calculator.calculateFee(any(), any(), any(), any()) } returns 0.0
        useCase = GetStockInventoryUseCase(calculator)
    }

    private var nextId = 1L

    private fun tx(
        type: TransactionType,
        date: LocalDate,
        price: Double = 0.0,
        shares: Int = 0,
        total: Double = 0.0,
        symbol: String = "2330"
    ) = TransactionItem(
        id = nextId++,
        accountId = 1L,
        type = type,
        symbol = symbol,
        name = "測試$symbol",
        price = price,
        shares = shares,
        date = date,
        fee = 0.0,
        note = "",
        dividend = 0.0,
        total = total,
        participatingShares = 0
    )

    private fun buy(date: LocalDate, shares: Int, price: Double, symbol: String = "2330") =
        tx(TransactionType.BUY, date, price, shares, symbol = symbol)

    private fun sell(date: LocalDate, shares: Int, price: Double, symbol: String = "2330") =
        tx(TransactionType.SELL, date, price, shares, symbol = symbol)

    private fun years(vararg trades: TransactionItem, method: CostBasisMethod = CostBasisMethod.AVERAGE_COST) =
        RealizedProfitStatistics.calculate(useCase.calculateBase(trades.toList(), method).realizedEvents)

    @Test
    fun `只有買進時沒有已實現損益`() {
        assertTrue(years(buy(LocalDate(2026, 1, 1), 1000, 100.0)).isEmpty())
    }

    @Test
    fun `賣出依賣出日期歸到對應年份`() {
        val result = years(
            buy(LocalDate(2024, 5, 1), 2000, 100.0),
            sell(LocalDate(2025, 3, 1), 1000, 120.0),   // +20000
            sell(LocalDate(2026, 2, 1), 1000, 90.0)     // -10000
        )
        assertEquals(listOf(2026, 2025), result.map { it.year })
        assertEquals(-10000.0, result[0].total, 0.001)
        assertEquals(20000.0, result[1].total, 0.001)
    }

    @Test
    fun `年度合計拆成獲利與虧損，並計算勝率次數`() {
        val year = years(
            buy(LocalDate(2026, 1, 1), 1000, 100.0, symbol = "A"),
            buy(LocalDate(2026, 1, 1), 1000, 100.0, symbol = "B"),
            sell(LocalDate(2026, 3, 1), 1000, 130.0, symbol = "A"),  // +30000
            sell(LocalDate(2026, 3, 5), 1000, 80.0, symbol = "B")    // -20000
        ).single()
        assertEquals(10000.0, year.total, 0.001)
        assertEquals(30000.0, year.gain, 0.001)
        assertEquals(-20000.0, year.loss, 0.001)
        assertEquals(2, year.tradeCount)
        assertEquals(1, year.winCount)
        assertEquals(10000.0, year.monthly[2], 0.001)
        assertEquals(listOf("A", "B"), year.byStock.map { it.symbol })
    }

    @Test
    fun `先進先出法使用最早批次的成本`() {
        val year = years(
            buy(LocalDate(2026, 1, 1), 1000, 100.0),
            buy(LocalDate(2026, 1, 2), 1000, 200.0),
            sell(LocalDate(2026, 6, 1), 1000, 300.0),
            method = CostBasisMethod.FIFO
        ).single()
        assertEquals(200000.0, year.total, 0.001)   // 300000 - 100000
        assertEquals(100000.0, year.byStock.single().cost, 0.001)
        assertEquals(200.0, year.byStock.single().profitPercent, 0.001)
    }

    @Test
    fun `年度合計等於持股的已實現損益合計`() {
        val trades = listOf(
            buy(LocalDate(2025, 1, 1), 3000, 100.0),
            sell(LocalDate(2025, 6, 1), 1000, 110.0),
            sell(LocalDate(2026, 6, 1), 1000, 95.0)
        )
        val base = useCase.calculateBase(trades)
        val yearsTotal = RealizedProfitStatistics.calculate(base.realizedEvents).sumOf { it.total }
        val positionsTotal = base.inventoryMap.values.sumOf { it.realizedProfit }
        assertEquals(positionsTotal, yearsTotal, 0.001)
    }
}
