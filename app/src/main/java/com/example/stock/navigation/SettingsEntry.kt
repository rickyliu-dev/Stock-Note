package com.example.stock.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.example.stock.feature.setting.SubSetting

/**
 * 設定頁「交易市場設定」分類的插入點，做法同 FeatureEntry。
 */
interface SettingsEntry {
    val title: String
    val icon: ImageVector
    val subItems: List<SubSetting>

    fun NavGraphBuilder.register(navController: NavHostController)
}
