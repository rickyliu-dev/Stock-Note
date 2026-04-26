package com.example.stock.navigation

sealed class AllScreens(val route: String) {
    object Home : AllScreens("home")
    object Detail : AllScreens("detail/{symbol}") {
        fun createRoute(symbol: String) = "detail/$symbol"
    }
    object AddTransaction : AllScreens("add_transaction/{symbol}/{id}") {
        fun createRoute(symbol: String?, id: Long = -1L) =
            "add_transaction/${symbol ?: "none"}/$id"
    }
    object Settings : AllScreens("settings_main")
    object AccountManagement : AllScreens("account_management")
    object CloudBackup : AllScreens("cloud_backup")

    // ==========================================
    // 設定專區的子畫面
    // ===

    // --- 台股群組 ---
    sealed class SettingsTw(route: String) : AllScreens(route) {
        object Sorting : SettingsTw("settings_tw_sorting")
        object Fees : SettingsTw("settings_tw_fees")
        // 未來擴充： object Tax : SettingsTw("settings_tw_tax")
    }

    // --- 美股群組 ---
    sealed class SettingsUs(route: String) : AllScreens(route) {
        object Sorting : SettingsUs("settings_us_sorting")
        object Fees : SettingsUs("settings_us_fees")
    }

    // --- 虛擬貨幣群組 ---
    sealed class SettingsCrypto(route: String) : AllScreens(route) {
        object Sorting : SettingsCrypto("settings_crypto_sorting")
        object Fees : SettingsCrypto("settings_crypto_fees")
        // 未來擴充： object Wallet : SettingsCrypto("settings_crypto_wallet")
    }

    companion object {
        const val ARG_SYMBOL = "symbol"
        const val TRANSACTIONS_ID = "id"
    }
}