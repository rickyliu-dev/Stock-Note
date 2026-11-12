package com.example.stock.navigation

sealed class AllScreens(val route: String) {
    object Home : AllScreens("home")
    object Detail : AllScreens("detail/{symbol}") {
        fun createRoute(symbol: String) = "detail/$symbol"
    }
    object AddTransaction : AllScreens("add_transaction/{symbol}/{id}/{name}") {
        fun createRoute(symbol: String?, name: String? = null, id: Long = -1L) =
            "add_transaction/${symbol ?: "none"}/$id/${name ?: "none"}"
    }
    object Settings : AllScreens("settings_main")
    object AccountManagement : AllScreens("account_management")
    object CloudBackup : AllScreens("cloud_backup")
    object Report : AllScreens("report")

    // ==========================================
    // 設定專區的子畫面
    // ===

    // --- 台股群組 ---
    sealed class SettingsTw(route: String) : AllScreens(route) {
        object Sorting : SettingsTw("settings_tw_sorting")
        object Fees : SettingsTw("settings_tw_fees")
        // 未來擴充： object Tax : SettingsTw("settings_tw_tax")
    }

    // 美股、虛擬貨幣設定的路由在 src/personal 的 SettingsEntry 裡

    companion object {
        const val ARG_SYMBOL = "symbol"
        const val TRANSACTIONS_ID = "id"
        const val ARG_NAME = "name"
    }
}