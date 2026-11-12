package com.example.stock.feature.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.stock.core.ui.component.screen.PlaceholderScreen
import com.example.stock.navigation.SettingsEntry
import javax.inject.Inject

/**
 * 設定頁的「美股設定」分類（只有 personal 有）。
 * 由 MarketSettingsModule 用 @IntoSet 放進 Set<SettingsEntry>。
 */
class UsStockSettingsEntry @Inject constructor() : SettingsEntry {
    override val title = "美股設定"
    override val icon: ImageVector = Icons.Default.Public
    override val subItems = listOf(
        SubSetting("自訂儀表板版面", ROUTE_SORTING),
        SubSetting("交易稅設定", ROUTE_FEES)
    )

    override fun NavGraphBuilder.register(navController: NavHostController) {
        composable(route = ROUTE_FEES) {
            PlaceholderScreen(
                title = "美股設定",
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = ROUTE_SORTING) {
            PlaceholderScreen(
                title = "美股設定",
                onBack = { navController.popBackStack() }
            )
        }
    }

    private companion object {
        // 沿用原本 AllScreens.SettingsUs 的路由字串
        const val ROUTE_SORTING = "settings_us_sorting"
        const val ROUTE_FEES = "settings_us_fees"
    }
}
