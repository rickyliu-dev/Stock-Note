package com.example.stock.core.data.source

import com.example.stock.core.data.model.StockDetail
import org.json.JSONObject

object YahooQuoteParser {

    fun parse(jsonStr: String): StockDetail? {
        return try {
            val resultObj = JSONObject(jsonStr)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
            val meta = resultObj.getJSONObject("meta")
            val currentPrice = meta.getDouble("regularMarketPrice")

            var previousClose = when {
                meta.has("regularMarketPreviousClose") -> meta.getDouble("regularMarketPreviousClose")
                meta.has("chartPreviousClose") -> meta.getDouble("chartPreviousClose")
                else -> 0.0
            }

            if (previousClose <= 0) {
                previousClose = firstValidClose(resultObj) ?: 0.0
            }
            if (previousClose <= 0) previousClose = currentPrice

            val change = currentPrice - previousClose
            val changePercent = if (previousClose > 0) (change / previousClose) * 100 else 0.0

            StockDetail(
                currentPrice = currentPrice,
                change = change,
                changePercent = changePercent
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun firstValidClose(resultObj: JSONObject): Double? = try {
        val closes = resultObj.getJSONObject("indicators")
            .getJSONArray("quote")
            .getJSONObject(0)
            .getJSONArray("close")
        (0 until closes.length())
            .map { closes.optDouble(it, 0.0) }
            .firstOrNull { it > 0 }
    } catch (e: Exception) {
        null
    }
}