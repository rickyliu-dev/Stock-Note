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
            if (response.isSuccessful) response.body?.string()?.let { YahooQuoteParser.parse(it) } else null
        } catch (e: Exception) {
            null
        }
    }
}