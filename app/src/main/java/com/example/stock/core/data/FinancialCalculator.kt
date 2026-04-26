package com.example.stock.core.data

import com.example.stock.core.data.model.TransactionType
import kotlin.math.floor

/**
 * 負責處理各種交易相關的財務計算
 */
interface FinancialCalculator {
    /**
     * 計算台股手續費：
     * 1. 原始手續費 = subtotal * feeRate / 100 (無條件捨去)
     * 2. 折扣後手續費 = 原始手續費 * discount / 10 (無條件捨去)
     * 3. 取大值 (折扣後手續費, 最低手續費)
     */
    fun calculateFee(
        subtotal: Double,
        feeRate: Double,
        discount: Double,
        minFee: Double
    ): Double

    fun calculateTax(subtotal: Double, isEtf: Boolean = false): Double

    fun calculateFinalTotal(
        type: TransactionType,
        subtotal: Double,
        fee: Double,
        tax: Double
    ): Double

    fun calculateStockDividendShares(price: Double, shares: Double): Int

    /**
     * 判斷是否為台灣 ETF
     * 規則：通常為 00 開頭的 4-6 位數字
     */
    fun isTaiwanEtf(symbol: String): Boolean

    /**
     * 計算分割後的總股數：
     * 分割比例 * 分割前股數
     */
    fun calculateSplitShares(ratio: Double, beforeShares: Double): Int

    /**
     * 計算減資後的總股數：
     * (1 - 減資比例) * 減資前股數
     * 在台灣，減資通常以「每股退還多少錢」或「減資百分比」表示。
     * 這裡我們統一使用： price 為退還金額 (或比例換算)，shares 為減資前股數。
     */
    fun calculateReductionShares(afterRatio: Double, beforeShares: Double): Int
}

class TaiwanFinancialCalculator : FinancialCalculator {
    
    override fun calculateFee(
        subtotal: Double,
        feeRate: Double,
        discount: Double,
        minFee: Double
    ): Double {
        if (subtotal <= 0) return 0.0
        
        // 台股精確公式修正：
        // 費率(feeRate) 例如 0.1425 (%)
        // 折扣(discount) 例如 6 (折)
        
        // 1. 原始手續費 = 成交金額 * 費率(%)
        // 4000 * (0.1425 / 100) = 5.7
        val rawFeeValue = subtotal * (feeRate / 100.0)
        
        // 2. 台股規則：原始手續費無條件捨去
        // floor(5.7) = 5
        val rawFeeFloor = floor(rawFeeValue)
        
        // 3. 計算折扣後手續費並再次捨去
        // 5 * (6 / 10) = 3.0 -> floor(3.0) = 3
        val discountedFee = floor(rawFeeFloor * (discount / 10.0))

        // 4. 與最低手續費 (低收) 比較
        return maxOf(minFee, discountedFee)
    }

    override fun calculateTax(subtotal: Double, isEtf: Boolean): Double {
        // 台股證交稅也是無條件捨去
        val rate = if (isEtf) MarketConstants.Taiwan.ETF_TAX_RATE else MarketConstants.Taiwan.TRANSACTION_TAX_RATE
        return floor(subtotal * rate)
    }

    override fun isTaiwanEtf(symbol: String): Boolean {
        // 台灣 ETF 判斷邏輯：
        // 1. 00 開頭 (如 0050, 0056, 00878)
        // 2. 01 開頭的權證或特定類型 (在此簡化為 00 開頭)
        // 3. 長度可能為 4~6 碼純數字
        return (symbol.startsWith("00") || symbol.startsWith("01")) && symbol.all { it.isDigit() }
    }

    override fun calculateFinalTotal(
        type: TransactionType,
        subtotal: Double,
        fee: Double,
        tax: Double
    ): Double {
        return when (type) {
            TransactionType.BUY -> subtotal + fee
            TransactionType.SELL -> subtotal - fee - tax
            TransactionType.DIVIDEND -> maxOf(0.0, subtotal - fee)
            TransactionType.STOCK_DIVIDEND -> 0.0
            TransactionType.DEPOSIT -> subtotal
            TransactionType.WITHDRAW -> subtotal
            TransactionType.ADJUSTMENT -> subtotal
            TransactionType.CAPITAL_REDUCTION -> subtotal // 減資退還現金
            TransactionType.SPLIT -> 0.0 // 分割不涉及現金流
        }
    }

    override fun calculateStockDividendShares(price: Double, shares: Double): Int {
        // 股票股利計算：(配股率 / 10) * 持有股數
        return floor((price / 10.0) * shares).toInt()
    }

    override fun calculateSplitShares(ratio: Double, beforeShares: Double): Int {
        return floor(ratio * beforeShares).toInt()
    }

    override fun calculateReductionShares(afterRatio: Double, beforeShares: Double): Int {
        // 假設 afterRatio 是剩下的比例 (例如減資 20%，則 afterRatio = 0.8)
        return floor(afterRatio * beforeShares).toInt()
    }
}
