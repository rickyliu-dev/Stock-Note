package com.example.stock.core.data.model

import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.repository.SettingsRepository
import kotlin.math.roundToInt

fun calculateNetValue(marketValue: Double, settings: SettingsRepository.TwSettings): Double {
    if (marketValue <= 0) return 0.0

    val rawFee = marketValue * (settings.feeRate.toDouble() / 100.0)
    val estSellFee = maxOf(
        settings.minFee.toDouble(),
        (rawFee * (settings.discount.toDouble() / 10.0)).roundToInt().toDouble()
    )
    val estTax = (marketValue * MarketConstants.Taiwan.TRANSACTION_TAX_RATE).roundToInt().toDouble()

    return marketValue - estSellFee - estTax
}