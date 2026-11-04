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
import com.example.stock.feature.report.reportGraph
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
            },
            onNavigateToReport = {
                navController.navigate(AllScreens.Report.route)
            },
            onNavigateToFeature = { route ->
                navController.navigate(route)
            }
        )

        // flavor 提供的功能（basic 為空集合）
        mainViewModel.featureEntries.forEach { entry ->
            with(entry) { register(navController) }
        }

        // 統計報表
        reportGraph(
            onBackClick = { navController.popBackStack() }
        )

        // 詳情頁
        detailGraph(
            onBackClick = {
                navController.popBackStack()
            },
            onNavigateToAdd = { symbol, name ->
                navController.navigate(AllScreens.AddTransaction.createRoute(symbol, name = name))
            },
            onNavigateToEdit = { symbol, name, id ->
                navController.navigate(AllScreens.AddTransaction.createRoute(symbol = symbol, name = name, id = id))
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

        // flavor 提供的設定分類（basic 為空集合）
        mainViewModel.settingsEntries.forEach { entry ->
            with(entry) { register(navController) }
        }
    }
}