package com.example.stock.feature.detail.component

import androidx.compose.ui.graphics.Color

// Detail頁面使用，讀取設定頁中的id來排序和決定顯示的數值及顏色
data class DashboardMetric(
    val id: String,      // 未來用來識別是哪一個欄位，方便存取排序設定
    val title: String,
    val value: String,
    val valueColor: Color = Color.Black // 允許個別設定顏色 (例如日損益是紅/綠色)
)

data class FormattedValue(
    val text: String,
    val rawValue: Double
)

data class ProfitDisplay(
    val amountText: String,
    val percentageText: String,
    val color: Color,
    val rawValue: Double
)

data class StockSummary(
    val remainingShares: Double = 0.0,
    val avgCost: Double = 0.0,
    val totalBuyAmount: Double = 0.0,
    val totalSellAmount: Double = 0.0,
    val dividendAmount: Double = 0.0,
    val currentPrice: Double = 0.0,
    val yesterdayPrice: Double = 0.0,
    val buyShares: Double = 0.0,
    val sellShares: Double = 0.0,
    val avgBuyAmount: Double = 0.0, // 不含費用的買入價款
    val netMarketValue: Double = 0.0,
    val yesterdayNetValue: Double = 0.0
)