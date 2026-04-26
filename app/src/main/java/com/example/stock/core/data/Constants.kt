package com.example.stock.core.data

object EnterConstants {
    const val DEBOUNCE_DELAY = 500L

}
object MarketConstants {

    // 台股相關規則
    object Taiwan {
        /** 一般證交稅率 (0.3%) */
        const val TRANSACTION_TAX_RATE = 0.003

        /** 現股當沖證交稅率 (0.15%)
        未來如果你要做當沖功能，這裡就派上用場了 */
        const val DAY_TRADING_TAX_RATE = 0.0015

        /** ETF證交稅 */
        const val ETF_TAX_RATE = 0.001

        /** 預設原始手續費率 (0.1425%) */
        const val DEFAULT_FEE_RATE = "0.1425"

        /** 預設最低手續費 (20元) */
        const val DEFAULT_MIN_FEE = "20"

        /** 預扣手續費開關 */
        const val SHOW_PRE_DEDUCT = true

        /** 手續費折數 */
        const val DISCOUNT = "6"
    }

    // 美股相關規則 (未來擴充用)
    object USA {
        /** 美股通常免證交稅，但有 SEC Fee 或賣出稅，可以在此定義 */
        const val SEC_FEE_RATE = 0.000008 // 舉例
    }
}