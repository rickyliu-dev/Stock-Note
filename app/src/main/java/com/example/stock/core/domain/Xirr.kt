package com.example.stock.core.domain

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.pow

/**
 * 年化報酬率 (XIRR)：考慮每筆現金流發生的日期，求出使淨現值為 0 的年利率
 * 與 Excel 的 XIRR 相同，一年以 365 天計算
 */
object Xirr {

    /** amount：投入為負數，取回 (含期末市值) 為正數 */
    data class CashFlow(val date: LocalDate, val amount: Double)

    private const val TOLERANCE = 1e-7
    private const val MIN_RATE = -0.999999

    /**
     * 回傳年化報酬率 (0.1 代表 10%)；
     * 現金流沒有同時包含正負數、或無法收斂時回傳 null
     */
    fun calculate(flows: List<CashFlow>): Double? {
        val valid = flows.filter { it.amount != 0.0 }
        if (valid.none { it.amount > 0 } || valid.none { it.amount < 0 }) return null

        val start = valid.minOf { it.date.toEpochDays() }
        val years = valid.map { (it.date.toEpochDays() - start) / 365.0 }
        val amounts = valid.map { it.amount }

        fun npv(rate: Double): Double =
            amounts.indices.sumOf { amounts[it] / (1 + rate).pow(years[it]) }

        fun derivative(rate: Double): Double =
            amounts.indices.sumOf { -years[it] * amounts[it] / (1 + rate).pow(years[it] + 1) }

        // 1. 先用牛頓法，通常幾次就收斂
        var rate = 0.1
        repeat(100) {
            val value = npv(rate)
            if (abs(value) < TOLERANCE) return rate
            val d = derivative(rate)
            if (d == 0.0 || !d.isFinite()) return@repeat
            val next = rate - value / d
            if (!next.isFinite() || next <= MIN_RATE) return@repeat
            if (abs(next - rate) < TOLERANCE) return next
            rate = next
        }

        // 2. 牛頓法失敗時改用二分法 (例如極短期間或報酬極端時)
        var low = MIN_RATE
        var high = 1.0
        var npvLow = npv(low)
        var npvHigh = npv(high)
        var expand = 0
        while (npvLow * npvHigh > 0 && expand < 60) {
            high *= 2
            npvHigh = npv(high)
            expand++
        }
        if (npvLow * npvHigh > 0 || !npvHigh.isFinite()) return null

        repeat(300) {
            val mid = (low + high) / 2
            val npvMid = npv(mid)
            if (abs(npvMid) < TOLERANCE || (high - low) / 2 < TOLERANCE) return mid
            if (npvMid * npvLow > 0) {
                low = mid
                npvLow = npvMid
            } else {
                high = mid
            }
        }
        return (low + high) / 2
    }
}
