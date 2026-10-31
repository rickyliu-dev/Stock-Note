package com.example.stock.feature.home

import com.example.stock.core.data.model.AssetSnapshot

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
        val historyData: List<AssetSnapshot>
    ) : DashboardPage()
}
