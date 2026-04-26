package com.example.stock.feature.addTransaction

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.stock.navigation.AllScreens

fun NavGraphBuilder.addTransactionGraph(
    onBackClick: () -> Unit
) {
    composable(
        route = AllScreens.AddTransaction.route,
        arguments = listOf(
            navArgument(AllScreens.ARG_SYMBOL) {
                type = NavType.StringType
                nullable = true
            },
            navArgument(AllScreens.TRANSACTIONS_ID) {
                type = NavType.LongType;
                defaultValue = -1L
            }
        )
    ) {

        AddTransactionScreen(
            onBack = onBackClick,
            onSaveSuccess = { onBackClick() }
        )
    }
}