package com.example.stock.feature.setting

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.stock.core.ui.component.screen.PlaceholderScreen
import com.example.stock.feature.setting.twStock.DashboardSortingScreen
import com.example.stock.feature.setting.twStock.TaiwanStockSettingsScreen
import com.example.stock.navigation.AllScreens

fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    onBackClick: () -> Unit
) {
    navigation(
        startDestination = AllScreens.Settings.route,
        route = "settings_root"
    ) {
        // 設定主頁面
        composable(route = AllScreens.Settings.route) {
            SettingsMainScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                },
                onBack = onBackClick
            )
        }

        // 雲端備份
        composable(route = AllScreens.CloudBackup.route) {
            CloudBackupScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 帳戶管理
        composable(route = AllScreens.AccountManagement.route) {
            AccountManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 台股設定
        composable(route = AllScreens.SettingsTw.Fees.route) {
            TaiwanStockSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = AllScreens.SettingsTw.Sorting.route) {
            DashboardSortingScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 美股設定 (佔位)
        composable(route = AllScreens.SettingsUs.Fees.route) {
            PlaceholderScreen(
                title = "美股設定",
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = AllScreens.SettingsUs.Sorting.route) {
            PlaceholderScreen(
                title = "美股設定",
                onBack = { navController.popBackStack() }
            )
        }

        // 虛擬貨幣設定 (佔位)
        composable(route = AllScreens.SettingsCrypto.Fees.route) {
            PlaceholderScreen(
                title = "虛擬貨幣設定",
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = AllScreens.SettingsCrypto.Sorting.route) {
            PlaceholderScreen(
                title = "虛擬貨幣設定",
                onBack = { navController.popBackStack() }
            )
        }
    }
}