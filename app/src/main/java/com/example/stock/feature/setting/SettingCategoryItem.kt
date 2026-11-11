package com.example.stock.feature.setting

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.stock.navigation.SettingsEntry

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
