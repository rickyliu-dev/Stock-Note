package com.example.stock.core.data.enumClass

enum class DashboardIds(val key: String) {
    /** 平均成本 */
    AVG_COST("avg_cost"),
    /** 平均買價 */
    AVG_BUY("avg_buy"),
    /** 持有股數 */
    SHARES("shares"),
    /** 總市值 */
    MARKET_VAL("market_val"),
    /** 總成本 */
    TOTAL_COST("total_cost"),
    /** 股息 */
    DIVIDEND("dividend"),
    /** 今日損益 */ // 順便幫你抓到一個小錯字：金日 -> 今日
    DAILY_PROFIT("daily_profit"),
    /** 未實現損益 */
    UNREALIZED("unrealized"),
    /** 已實現損益 */
    REALIZED("realized"),
    /** 累積損益 */
    CUMULATIVE_PROFIT("cumulative_profit");

    // 💡 超實用小工具：從字串反向找回 Enum
    companion object {
        fun fromKey(key: String): DashboardIds? {
            return entries.find { it.key == key }
        }
    }
}