package com.example.stock.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.example.stock.feature.setting.SubSetting

/**
 * 首頁的功能入口插入點。
 * 核心不能 import flavor 的類別，所以由各 flavor 用 Hilt @IntoSet 提供實作；
 * 沒有提供時（例如 basic）會注入空集合，見 FeatureEntryModule。
 */
interface FeatureEntry {
    val title: String
    val icon: ImageVector
    val route: String

    fun NavGraphBuilder.register(navController: NavHostController)
}

/**
 * 設定頁「交易市場設定」分類的插入點，做法同 FeatureEntry。
 */
interface SettingsEntry {
    val title: String
    val icon: ImageVector
    val subItems: List<SubSetting>

    fun NavGraphBuilder.register(navController: NavHostController)
}
