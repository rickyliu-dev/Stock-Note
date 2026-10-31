package com.example.stock.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class TwMarketHoursTest {

    // 2026-10-30 是星期五
    private fun at(day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, TwMarketHours.ZONE)

    @Test
    fun `週末不更新`() {
        assertFalse(TwMarketHours.shouldAutoUpdate(at(31, 10, 0), null))  // 星期六
        assertFalse(TwMarketHours.shouldAutoUpdate(at(25, 14, 0), null))  // 星期日
    }

    @Test
    fun `開盤前不更新`() {
        assertFalse(TwMarketHours.shouldAutoUpdate(at(30, 8, 59), null))
    }

    @Test
    fun `盤中尚未更新過就立即更新`() {
        assertTrue(TwMarketHours.shouldAutoUpdate(at(30, 9, 0), null))
    }

    @Test
    fun `盤中距離上次更新未滿5分鐘不更新`() {
        assertFalse(TwMarketHours.shouldAutoUpdate(at(30, 10, 4), at(30, 10, 0)))
    }

    @Test
    fun `盤中距離上次更新滿5分鐘就更新`() {
        assertTrue(TwMarketHours.shouldAutoUpdate(at(30, 10, 5), at(30, 10, 0)))
    }

    @Test
    fun `收盤後若收盤前更新過則再更新一次取得收盤價`() {
        assertTrue(TwMarketHours.shouldAutoUpdate(at(30, 13, 35), at(30, 13, 32)))
        assertTrue(TwMarketHours.shouldAutoUpdate(at(30, 20, 0), at(29, 13, 40)))  // 昨天的收盤價
    }

    @Test
    fun `收盤後已更新過就不再更新`() {
        assertFalse(TwMarketHours.shouldAutoUpdate(at(30, 20, 0), at(30, 13, 40)))
    }
}
