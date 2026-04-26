package com.example.stock.feature.home.component



data class HomeUiState(
    val summary: PortfolioSummary = PortfolioSummary(),
    val stockList: List<StockDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class StockInventory(
    val shares: Double,
    val totalCost: Double
)

data class StockDisplayItem(
    val symbol: String,
    val name: String,
    val shares: Double,
    val currentPrice: Double,
    val change: Double,
    val changePercent: Double,
    val avgCost: Double,
    val profit: Double,
    val profitPercent: Double,
    val marketValue: Double, // 原始市值
    val subtitle: String? = null
)

data class PortfolioSummary(
    // 總資產 (現值)
    val totalAssets: Double = 0.0,
    // 現金餘額
    val cashBalance: Double = 0.0,
    // 目前持股的損益
    val unrealizedProfit: Double = 0.0,
    // 累積損益 (已實現 + 未實現 + 股息)
    val cumulativeProfit: Double = 0.0,
    // 今日損益 (跟昨收價比)
    val dailyProfit: Double = 0.0,
    // 今日漲跌幅 (%)
    val dailyRate: Double = 0.0
)