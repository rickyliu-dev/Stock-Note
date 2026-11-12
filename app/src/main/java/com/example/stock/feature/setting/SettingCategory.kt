package com.example.stock.feature.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.stock.navigation.AllScreens

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

    // 美股、虛擬貨幣設定只在 personal，由 SettingsEntry 提供（見 src/personal）
}
