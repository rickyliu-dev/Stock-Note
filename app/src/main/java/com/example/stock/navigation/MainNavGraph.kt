package com.example.stock.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.MainViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.stock.AppNavAnimations
import com.example.stock.feature.addTransaction.addTransactionGraph
import com.example.stock.feature.detail.detailGraph
import com.example.stock.feature.home.homeGraph
import com.example.stock.feature.setting.settingsGraph

@Composable
fun MainNavGraph(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = AllScreens.Home.route,
        enterTransition = AppNavAnimations.enterTransition,
        exitTransition = AppNavAnimations.exitTransition,
        popEnterTransition = AppNavAnimations.popEnterTransition,
        popExitTransition = AppNavAnimations.popExitTransition
    ) {
        // 首頁
        homeGraph(
            mainViewModel = mainViewModel,
            onNavigateToDetail = { symbol ->
                navController.navigate(AllScreens.Detail.createRoute(symbol))
            },
            onNavigateToAdd = {
                navController.navigate(AllScreens.AddTransaction.createRoute(null))
            },
            onNavigateToSettings = {
                navController.navigate(AllScreens.Settings.route)
            }
        )

        // 詳情頁
        detailGraph(
            onBackClick = {
                navController.popBackStack()
            },
            onNavigateToAdd = { symbol ->
                navController.navigate(AllScreens.AddTransaction.createRoute(symbol))
            },
            onNavigateToEdit = { symbol, id ->
                navController.navigate(AllScreens.AddTransaction.createRoute(symbol, id))
            }
        )

        // 新增交易
        addTransactionGraph(
            onBackClick = { navController.popBackStack() }
        )

        settingsGraph(
            navController = navController,
            onBackClick = { navController.popBackStack() }
        )
    }
}