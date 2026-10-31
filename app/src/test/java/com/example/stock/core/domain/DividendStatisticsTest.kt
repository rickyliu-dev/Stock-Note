package com.example.stock.core.domain

import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DividendStatisticsTest {

    private var nextId = 1L

    private fun tx(
        type: TransactionType,
        date: LocalDate,
        total: Double,
        symbol: String = "2330"
    ) = TransactionItem(
        id = nextId++,
        accountId = 1L,
        type = type,
        symbol = symbol,
        name = "測試$symbol",
        price = 0.0,
        shares = 0,
        date = date,
        fee = 0.0,
        note = "",
        dividend = 0.0,
        total = total,
        participatingShares = 0
    )

    private fun dividend(year: Int, month: Int, total: Double, symbol: String = "2330") =
        tx(TransactionType.DIVIDEND, LocalDate(year, month, 15), total, symbol)

    @Test
    fun `沒有股息時回傳空清單`() {
        val result = DividendStatistics.calculate(
            listOf(tx(TransactionType.BUY, LocalDate(2026, 1, 1), 1000.0))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `只統計現金股息，不含股票股利與其他交易`() {
        val result = DividendStatistics.calculate(
            listOf(
                dividend(2026, 3, 500.0),
                tx(TransactionType.STOCK_DIVIDEND, LocalDate(2026, 3, 1), 999.0),
                tx(TransactionType.DEPOSIT, LocalDate(2026, 3, 1), 10000.0)
            )
        )
        assertEquals(1, result.size)
        assertEquals(500.0, result[0].total, 0.001)
    }

    @Test
    fun `依年份分組，由新到舊`() {
        val result = DividendStatistics.calculate(
            listOf(
                dividend(2024, 7, 100.0),
                dividend(2026, 1, 300.0),
                dividend(2025, 8, 200.0)
            )
        )
        assertEquals(listOf(2026, 2025, 2024), result.map { it.year })
        assertEquals(listOf(300.0, 200.0, 100.0), result.map { it.total })
    }

    @Test
    fun `每月金額加總正確，固定 12 個月`() {
        val year = DividendStatistics.calculate(
            listOf(
                dividend(2026, 1, 100.0),
                dividend(2026, 1, 50.0, symbol = "0056"),
                dividend(2026, 12, 80.0)
            )
        ).single()
        assertEquals(12, year.monthly.size)
        assertEquals(150.0, year.monthly[0], 0.001)
        assertEquals(80.0, year.monthly[11], 0.001)
        assertEquals(0.0, year.monthly[5], 0.001)
        assertEquals(230.0, year.total, 0.001)
    }

    @Test
    fun `依股票彙總，金額由大到小並記錄次數`() {
        val year = DividendStatistics.calculate(
            listOf(
                dividend(2026, 1, 100.0, symbol = "2330"),
                dividend(2026, 7, 120.0, symbol = "2330"),
                dividend(2026, 3, 500.0, symbol = "0056")
            )
        ).single()
        assertEquals(listOf("0056", "2330"), year.byStock.map { it.symbol })
        assertEquals(220.0, year.byStock[1].amount, 0.001)
        assertEquals(2, year.byStock[1].count)
    }
}
