package com.example.stock.core.domain.model

/**
 * 統一的庫存與損益模型
 */
data class StockPosition(
    val symbol: String,
    val name: String = "",
    val shares: Double = 0.0,
    val avgCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalBuyAmount: Double = 0.0, // 累計買入總額 (含費用)
    val totalSellAmount: Double = 0.0, // 累計賣出總額 (扣除費用)
    val buyShares: Double = 0.0,
    val sellShares: Double = 0.0,
    val dividendAmount: Double = 0.0,
    val avgBuyPrice: Double = 0.0, // 純買入平均價 (不含費用)
    
    // 即時計算指標 (依據市價)
    val currentPrice: Double = 0.0,
    val yesterdayPrice: Double = 0.0,
    val marketValue: Double = 0.0, // 總市值 (毛)
    val netMarketValue: Double = 0.0, // 預估淨現值 (扣除賣出稅費)
    val yesterdayNetValue: Double = 0.0,
    
    // 損益
    val unrealizedProfit: Double = 0.0, // 未實現損益 (淨現值 - 剩餘成本)
    val realizedProfit: Double = 0.0, // 已實現損益
    val dailyProfit: Double = 0.0, // 今日損益
    val totalProfit: Double = 0.0 // 總損益 (含息與否由 UseCase 決定)
)