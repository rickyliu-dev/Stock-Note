package com.example.stock.feature.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.stock.navigation.AllScreens
import com.example.stock.navigation.SettingsEntry

sealed class SettingCategory(
    val title: String,
    val icon: ImageVector,
    val subItems: List<SubSetting>
) {

    object TaiwanStock : SettingCategory(
        title = "台股設定",
        icon = Icons.AutoMirrored.Filled.ShowChart,
        subItems = listOf(
            SubSetting("自訂儀表板版面", AllScreens.SettingsTw.Sorting.route),
            SubSetting("手續費與預扣設定", AllScreens.SettingsTw.Fees.route)
        )
    )

    object UsStock : SettingCategory(
        title = "美股設定",
        icon = Icons.Default.Public,
        subItems = listOf(
            SubSetting("自訂儀表板版面", AllScreens.SettingsUs.Sorting.route),
            SubSetting("交易稅設定", AllScreens.SettingsUs.Fees.route)
        )
    )

    object Crypto : SettingCategory(
        title = "虛擬貨幣設定",
        icon = Icons.Default.CurrencyBitcoin,
        subItems = listOf(
            SubSetting("自訂儀表板版面", AllScreens.SettingsUs.Sorting.route),
            SubSetting("Gas Fee 設定", AllScreens.SettingsCrypto.Fees.route)
        )
    )
}

/**
 * 設定頁分類清單的顯示資料。
 * SettingCategory 是 sealed class，flavor 無法繼承，所以核心分類與 SettingsEntry 都先轉成這個型別再顯示。
 */
data class SettingCategoryItem(
    val title: String,
    val icon: ImageVector,
    val subItems: List<SubSetting>
)

fun SettingCategory.toItem() = SettingCategoryItem(title, icon, subItems)

fun SettingsEntry.toItem() = SettingCategoryItem(title, icon, subItems)

data class SubSetting(
    val title: String,
    val route: String
)

