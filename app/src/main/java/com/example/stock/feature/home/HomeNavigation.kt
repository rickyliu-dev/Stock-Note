package com.example.stock.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.stock.navigation.AllScreens

import com.example.stock.MainViewModel

fun NavGraphBuilder.homeGraph(
    mainViewModel: MainViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    composable(route = AllScreens.Home.route) {
        HomeScreen(
            mainViewModel = mainViewModel,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToAdd = onNavigateToAdd,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}