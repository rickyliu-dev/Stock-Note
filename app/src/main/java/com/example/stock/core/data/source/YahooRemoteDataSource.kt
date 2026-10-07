package com.example.stock.core.data.source

import com.example.stock.core.data.model.StockDetail
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooRemoteDataSource @Inject constructor(private val client: OkHttpClient) {
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun search(query: String): List<Pair<String, String>> {
        return try {
            val url = "https://query1.finance.yahoo.com/v1/finance/search?q=$query&lang=en-US&region=US&quotesCount=10"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return emptyList()
            val quotes = JSONObject(jsonStr).optJSONArray("quotes") ?: return emptyList()

            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until quotes.length()) {
                val item = quotes.getJSONObject(i)
                val symbol = item.optString("symbol")
                val name = item.optString("shortname", item.optString("longname"))
                val type = item.optString("quoteType")
                if (type == "EQUITY" || type == "ETF") {
                    val cleanSymbol = symbol.replace(".TW", "").replace(".TWO", "")
                    list.add(cleanSymbol to name)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchQuote(yahooSymbol: String): StockDetail? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol?interval=1d&range=2d"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string()?.let { parseQuoteFromJson(it) } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQuoteFromJson(jsonStr: String): StockDetail? {
        return try {
            val resultObj = JSONObject(jsonStr).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val meta = resultObj.getJSONObject("meta")
            val currentPrice = meta.getDouble("regularMarketPrice")

            var previousClose = when {
                meta.has("regularMarketPreviousClose") -> meta.getDouble("regularMarketPreviousClose")
                meta.has("chartPreviousClose") -> meta.getDouble("chartPreviousClose")
                else -> 0.0
            }

            if (previousClose <= 0) {
                try {
                    val closeArray = resultObj.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                    for (i in 0 until closeArray.length()) {
                        val v = closeArray.optDouble(i, 0.0)
                        if (v > 0) { previousClose = v; break }
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            if (previousClose <= 0) previousClose = currentPrice

            val change = currentPrice - previousClose
            val changePercent = if (previousClose > 0) (change / previousClose) * 100 else 0.0

            StockDetail(currentPrice = currentPrice, change = change, changePercent = changePercent)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}