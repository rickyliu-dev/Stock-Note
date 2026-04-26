package com.example.stock.core.domain

import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.model.TransactionType
import javax.inject.Inject

/**
 * 封裝交易計算邏輯的 UseCase
 */
class CalculateTransactionUseCase @Inject constructor(
    private val financialCalculator: FinancialCalculator
) {
    data class Params(
        val type: TransactionType,
        val price: Double,
        val shares: Double,
        val feeRate: Double,
        val discount: Double,
        val minFee: Double,
        val customFee: Double? = null, // 使用者手動輸入的手續費
        val symbol: String = "" // 新增 symbol 用於判斷 ETF
    )

    data class Result(
        val subtotal: Double,
        val fee: Double,
        val tax: Double,
        val finalTotal: Double,
        val stockDividendShares: Int = 0
    )

    operator fun invoke(params: Params): Result {
        val subtotal = params.price * params.shares
        
        // 1. 計算手續費 (優先使用手動輸入，若無則自動計算)
        // 注意：股息(DIVIDEND)不需要計算交易手續費，僅扣除匯費 (通常也是由使用者手動輸入 customFee)
        val isTrade = params.type == TransactionType.BUY || params.type == TransactionType.SELL || params.type == TransactionType.CAPITAL_REDUCTION
        
        val fee = if (isTrade) {
            params.customFee ?: financialCalculator.calculateFee(
                subtotal = subtotal,
                feeRate = params.feeRate,
                discount = params.discount,
                minFee = params.minFee
            )
        } else {
            // 對於股息，如果有手動輸入的費用(如匯費)，則使用之；否則為 0
            params.customFee ?: 0.0
        }

        // 2. 計算稅金 (僅賣出時產生)
        val tax = if (params.type == TransactionType.SELL) {
            val isEtf = financialCalculator.isTaiwanEtf(params.symbol)
            financialCalculator.calculateTax(subtotal, isEtf)
        } else 0.0

        // 3. 計算總額
        val finalTotal = financialCalculator.calculateFinalTotal(
            type = params.type,
            subtotal = subtotal,
            fee = fee,
            tax = tax
        )

        // 4. 特殊處理：股票股利 / 分割 / 減資 計算
        val stockDividendShares = when (params.type) {
            TransactionType.STOCK_DIVIDEND -> financialCalculator.calculateStockDividendShares(params.price, params.shares)
            TransactionType.SPLIT -> financialCalculator.calculateSplitShares(params.price, params.shares)
            TransactionType.CAPITAL_REDUCTION -> financialCalculator.calculateReductionShares(params.price, params.shares)
            else -> 0
        }

        return Result(
            subtotal = subtotal,
            fee = fee,
            tax = tax,
            finalTotal = finalTotal,
            stockDividendShares = stockDividendShares
        )
    }
}
