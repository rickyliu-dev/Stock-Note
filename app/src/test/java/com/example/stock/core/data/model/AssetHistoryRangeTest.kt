package com.example.stock.core.data.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetHistoryRangeTest {

    private val today = LocalDate(2026, 10, 31)

    private val snapshots = listOf(
        "2025-09-30", "2025-10-31", "2026-09-30", "2026-10-01", "2026-10-24", "2026-10-25", "2026-10-31"
    ).map { AssetSnapshot(accountId = 1L, date = it, totalAssets = 100.0, cashBalance = 0.0) }

    private fun dates(range: AssetHistoryRange) = range.filter(snapshots, today).map { it.date }

    @Test
    fun `1W 包含 7 天前當天`() {
        assertEquals(listOf("2026-10-24", "2026-10-25", "2026-10-31"), dates(AssetHistoryRange.WEEK))
    }

    @Test
    fun `1M 從上個月同一天開始`() {
        // 10/31 減一個月為 9/30 (9 月沒有 31 日)，9/30 當天包含在內
        assertEquals(
            listOf("2026-09-30", "2026-10-01", "2026-10-24", "2026-10-25", "2026-10-31"),
            dates(AssetHistoryRange.MONTH)
        )
    }

    @Test
    fun `1Y 從去年同一天開始`() {
        assertEquals(snapshots.drop(1).map { it.date }, dates(AssetHistoryRange.YEAR))
    }

    @Test
    fun `ALL 全部保留`() {
        assertEquals(snapshots.map { it.date }, dates(AssetHistoryRange.ALL))
    }
}
