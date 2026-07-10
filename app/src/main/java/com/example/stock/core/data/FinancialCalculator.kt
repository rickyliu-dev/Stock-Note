package com.example.stock.core.data

import com.example.stock.core.data.model.TransactionType
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 負責處理各種交易相關的財務計算
 */
interface FinancialCalculator {
    /**
     * 計算台股手續費：
     * 1. 原始手續費 = subtotal * feeRate / 100 (無條件進位)
     * 2. 折扣後手續費 = 原始手續費 * discount / 10 (無條件進位)
     * 3. 取大值 (折扣後手續費, 最低手續費)
     */
    fun calculateFee(
        subtotal: Double,
        feeRate: Double,
        discount: Double,
        minFee: Double
    ): Double

    fun calculateTax(
        subtotal: Double,
        isEtf: Boolean = false
    ): Double

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
     * 如果是現金減資，price 為退還金額 (每股)，減資比例 = price / 10
     * 減資後股數 = beforeShares * (1 - price / 10)
     */
    fun calculateReductionShares(price: Double, beforeShares: Double): Int
}

class TaiwanFinancialCalculator : FinancialCalculator {
    
    override fun calculateFee(
        subtotal: Double,
        feeRate: Double,
        discount: Double,
        minFee: Double
    ): Double {
        if (subtotal <= 0) return 0.0

        // 1. 原始手續費 = 成交金額 * 費率(%)
        val rawFeeValue = subtotal * (feeRate / 100.0)

        // 2. 直接計算折扣後手續費（保持浮點數精準度，最後再一起捨去）
        val discountedFeeValue = rawFeeValue * discount / 10.0

        // 3. 依國泰官方規範：元以下無條件捨去
        val finalCalculatedFee = floor(discountedFeeValue)

        // 4. 與最低手續費 (低收 1 元) 比較，取其大者
        return maxOf(minFee, finalCalculatedFee)
    }

    override fun calculateTax(subtotal: Double, isEtf: Boolean): Double {
        if (subtotal <= 0) return 0.0

        // 1. 取得對應稅率
        val rate = if (isEtf) {
            MarketConstants.Taiwan.ETF_TAX_RATE
        } else {
            MarketConstants.Taiwan.TRANSACTION_TAX_RATE
        }

        // 2. 元以下無條件捨去
        val calculatedTax = floor(subtotal * rate)

        // 3. 關鍵修正：若捨去後小於 1 元（即為 0.0），則強制以 1 元計收
        return if (calculatedTax < 1.0) 1.0 else calculatedTax
    }

    override fun isTaiwanEtf(symbol: String): Boolean {
        // 台灣 ETF 與權證 判斷邏輯：
        // 1. ETF: 00 開頭 (如 0050, 0056, 00878)
        // 2. 權證: 6 碼數字為主，或特定的權證代碼
        // 3. 2023/11 起權證證交稅降至 0.1%，與 ETF 相同
        val baseSymbol = symbol.substringBefore(".")
        val isEtf = (baseSymbol.startsWith("00") || baseSymbol.startsWith("01")) && baseSymbol.all { it.isDigit() }
        val isWarrant = baseSymbol.length == 6 && baseSymbol.any { it.isDigit() } // 簡單判定：6 碼通常是權證
        return isEtf || isWarrant
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
            TransactionType.CAPITAL_REDUCTION -> maxOf(0.0, subtotal - fee) // 減資退還現金需扣除手續費
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

    override fun calculateReductionShares(price: Double, beforeShares: Double): Int {
        // 台灣現金減資公式：減資後股數 = 減資前股數 * (1 - (每股退還現金 / 10))
        // 注意：這裡假設面額為 10 元
        val afterRatio = 1.0 - (price / 10.0)
        return floor(maxOf(0.0, afterRatio) * beforeShares).toInt()
    }
}
