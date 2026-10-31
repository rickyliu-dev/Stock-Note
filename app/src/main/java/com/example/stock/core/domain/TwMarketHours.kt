package com.example.stock.core.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 台股交易時段判斷，用來決定是否需要自動更新股價 (減少對 Yahoo 的請求)
 * 註：未處理國定假日，假日開著 App 仍會依平日規則更新
 */
object TwMarketHours {
    val ZONE: ZoneId = ZoneId.of("Asia/Taipei")
    private val OPEN: LocalTime = LocalTime.of(9, 0)
    // 13:30 收盤，多留 5 分鐘讓最後成交價更新完成
    private val CLOSE: LocalTime = LocalTime.of(13, 35)
    val UPDATE_INTERVAL: Duration = Duration.ofMinutes(5)

    /**
     * @param now 目前時間 (台北時區)
     * @param lastUpdate 上次更新時間，尚未更新過則為 null
     */
    fun shouldAutoUpdate(now: ZonedDateTime, lastUpdate: ZonedDateTime?): Boolean {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false

        val time = now.toLocalTime()
        return when {
            // 開盤前：股價還是前一天的收盤價，不需更新
            time < OPEN -> false
            // 盤中：距離上次更新超過間隔就更新
            time < CLOSE -> lastUpdate == null || Duration.between(lastUpdate, now) >= UPDATE_INTERVAL
            // 收盤後：今天收盤後還沒更新過，就更新一次取得收盤價
            else -> lastUpdate == null || lastUpdate < now.with(CLOSE)
        }
    }
}
