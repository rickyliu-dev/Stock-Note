package com.example.stock.feature.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.stock.core.ui.component.screen.PlaceholderScreen
import com.example.stock.navigation.SettingsEntry
import javax.inject.Inject

/**
 * 設定頁的「虛擬貨幣設定」分類（只有 personal 有）。
 * 由 MarketSettingsModule 用 @IntoSet 放進 Set<SettingsEntry>。
 */
class CryptoSettingsEntry @Inject constructor() : SettingsEntry {
    override val title = "虛擬貨幣設定"
    override val icon: ImageVector = Icons.Default.CurrencyBitcoin
    override val subItems = listOf(
        // 卡 2-2 時發現原本指到美股頁，卡 2b 修正為虛擬貨幣自己的頁面
        SubSetting("自訂儀表板版面", ROUTE_SORTING),
        SubSetting("Gas Fee 設定", ROUTE_FEES)
    )

    override fun NavGraphBuilder.register(navController: NavHostController) {
        composable(route = ROUTE_FEES) {
            PlaceholderScreen(
                title = "虛擬貨幣設定",
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = ROUTE_SORTING) {
            PlaceholderScreen(
                title = "虛擬貨幣設定",
                onBack = { navController.popBackStack() }
            )
        }
    }

    private companion object {
        // 沿用原本 AllScreens.SettingsCrypto 的路由字串
        const val ROUTE_SORTING = "settings_crypto_sorting"
        const val ROUTE_FEES = "settings_crypto_fees"
    }
}
