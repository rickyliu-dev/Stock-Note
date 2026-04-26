package com.example.stock.core.ui.component

import java.util.Locale

/**
 * 將 Double 轉換為財務顯示用的字串
 * @param decimals 小數點後要保留幾位 (台股總金額通常是 0，單價可能是 2)
 * @return 格式化後的字串，例如 "1,234" 或 "1,234.50"
 */
fun Double.toCurrencyString(decimals: Int = 0): String {
    // 1. 強制使用 Locale.US，保證格式永遠是 "1,234.56" 且數字為 0~9
    // 2. 加上 "," 代表要自動加上千分位分隔符號，這對股票金額非常重要！
    return String.format(Locale.US, "%,.${decimals}f", this)
}

// 如果你的變數有時候是 Float，也可以順便準備一個
fun Float.toCurrencyString(decimals: Int = 0): String {
    return String.format(Locale.US, "%,.${decimals}f", this)
}