package com.example.stock.feature.home

sealed class DashboardPage {
    data class ProfitOverview(
        val totalProfit: Double,
        val isCumulative: Boolean,
        val includeDividends: Boolean
    ) : DashboardPage()

    data class AssetPieChart(
        val data: Map<String, Double>
    ) : DashboardPage()

    data class AssetLineChart(
        val historyData: List<Pair<Long, Double>>
    ) : DashboardPage()
}
