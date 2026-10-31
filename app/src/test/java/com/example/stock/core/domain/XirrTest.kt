package com.example.stock.core.domain

import com.example.stock.core.domain.Xirr.CashFlow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class XirrTest {

    private fun xirr(vararg flows: CashFlow) = Xirr.calculate(flows.toList())

    @Test
    fun `投入一年後取回 110 為 10 趴`() {
        val r = xirr(
            CashFlow(LocalDate(2025, 1, 1), -1000.0),
            CashFlow(LocalDate(2026, 1, 1), 1100.0)
        )
        assertEquals(0.10, r!!, 1e-6)
    }

    @Test
    fun `與 Excel XIRR 範例結果相同`() {
        // Microsoft 文件中 XIRR 的範例，結果約 37.34%
        val r = xirr(
            CashFlow(LocalDate(2008, 1, 1), -10000.0),
            CashFlow(LocalDate(2008, 3, 1), 2750.0),
            CashFlow(LocalDate(2008, 10, 30), 4250.0),
            CashFlow(LocalDate(2009, 2, 15), 3250.0),
            CashFlow(LocalDate(2009, 4, 1), 2750.0)
        )
        assertEquals(0.373362535, r!!, 1e-6)
    }

    @Test
    fun `虧損時為負數`() {
        val r = xirr(
            CashFlow(LocalDate(2025, 1, 1), -1000.0),
            CashFlow(LocalDate(2026, 1, 1), 800.0)
        )
        assertEquals(-0.20, r!!, 1e-6)
    }

    @Test
    fun `極短期間的大幅獲利仍能算出結果`() {
        val r = xirr(
            CashFlow(LocalDate(2026, 1, 1), -1000.0),
            CashFlow(LocalDate(2026, 1, 11), 1100.0)
        )
        assertNotNull(r)
        // 10 天賺 10%：(1.1)^(365/10) - 1
        assertEquals(Math.pow(1.1, 36.5) - 1, r!!, 1e-3 * r)
    }

    @Test
    fun `只有投入或只有取回時無法計算`() {
        assertNull(xirr(CashFlow(LocalDate(2026, 1, 1), -1000.0)))
        assertNull(xirr(CashFlow(LocalDate(2026, 1, 1), 1000.0)))
        assertNull(xirr())
    }
}
