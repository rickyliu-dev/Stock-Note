package com.example.stock.feature.detail

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.stock.navigation.AllScreens

fun NavGraphBuilder.detailGraph(
    onBackClick: () -> Unit,
    onNavigateToAdd: (String) -> Unit,
    onNavigateToEdit: (String, Long) -> Unit
) {
    composable(
        route = AllScreens.Detail.route,
        arguments = listOf(navArgument(AllScreens.ARG_SYMBOL) { type = NavType.StringType })
    ) {
        HoldingsDetailScreen(
            onBack = onBackClick,
            onAddTransaction = onNavigateToAdd,
            onEditTransaction = onNavigateToEdit
        )
    }
}