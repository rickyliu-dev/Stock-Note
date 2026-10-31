package com.example.stock.feature.report

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.stock.navigation.AllScreens

fun NavGraphBuilder.reportGraph(
    onBackClick: () -> Unit
) {
    composable(route = AllScreens.Report.route) {
        ReportScreen(onBack = onBackClick)
    }
}
