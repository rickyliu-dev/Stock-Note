package com.example.stock.core.domain

import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReturnStatisticsTest {

    private val calculator = mockk<FinancialCalculator>()
    private lateinit var useCase: GetStockInventoryUseCase
    private val today = LocalDate(2026, 1, 1)

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

    private fun calculate(trades: List<TransactionItem>, quotes: Map<String, StockQuote> = emptyMap()): ReturnStatistics.PortfolioReturn? {
        val base = useCase.calculateBase(trades)
        val result = useCase.evaluate(base, quotes)
        return ReturnStatistics.calculate(base, result.positions, today)
    }

    @Test
    fun `沒有股票交易時回傳 null，入金不算`() {
        assertNull(calculate(listOf(tx(TransactionType.DEPOSIT, LocalDate(2025, 1, 1), total = 10000.0))))
    }

    @Test
    fun `持股中以今天市值當作期末取回`() {
        val r = calculate(
            listOf(tx(TransactionType.BUY, LocalDate(2025, 1, 1), price = 100.0, shares = 1000)),
            quotes = mapOf("2330" to StockQuote(110.0, 0.0, 0.0))
        )!!
        assertEquals(100000.0, r.invested, 0.001)
        assertEquals(110000.0, r.currentValue, 0.001)
        assertEquals(10000.0, r.profit, 0.001)
        assertEquals(0.10, r.xirr!!, 1e-6)
        assertTrue(r.stocks.single().isActive)
    }

    @Test
    fun `股息計入取回金額`() {
        val r = calculate(
            listOf(
                tx(TransactionType.BUY, LocalDate(2025, 1, 1), price = 100.0, shares = 1000),
                tx(TransactionType.DIVIDEND, LocalDate(2025, 7, 1), total = 5000.0)
            ),
            quotes = mapOf("2330" to StockQuote(100.0, 0.0, 0.0))
        )!!
        assertEquals(5000.0, r.received, 0.001)
        assertEquals(5000.0, r.profit, 0.001)
        assertTrue(r.xirr!! > 0.05)   // 股息提早半年領到，年化略高於 5%
    }

    @Test
    fun `已清倉的股票沒有期末市值，持股中排在前面`() {
        val r = calculate(
            listOf(
                tx(TransactionType.BUY, LocalDate(2025, 1, 1), price = 100.0, shares = 1000, symbol = "A"),
                tx(TransactionType.SELL, LocalDate(2025, 7, 1), price = 120.0, shares = 1000, symbol = "A"),
                tx(TransactionType.BUY, LocalDate(2025, 1, 1), price = 100.0, shares = 1000, symbol = "B")
            ),
            quotes = mapOf("B" to StockQuote(90.0, 0.0, 0.0))
        )!!
        assertEquals(listOf("B", "A"), r.stocks.map { it.symbol })
        val closed = r.stocks.last()
        assertFalse(closed.isActive)
        assertEquals(0.0, closed.currentValue, 0.001)
        assertEquals(20000.0, closed.profit, 0.001)
        // 整體：投入 200000，取回 120000 + 市值 90000
        assertEquals(10000.0, r.profit, 0.001)
    }
}
