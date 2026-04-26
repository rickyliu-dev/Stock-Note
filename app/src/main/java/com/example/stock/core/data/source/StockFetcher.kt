package com.example.stock.core.data.source

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.stock.core.data.model.StockDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import javax.inject.Singleton

enum class SearchRegion {
    TW,     // 僅台股 (只查本地清單，最快、全中文)
    US,     // 僅美股 (只查 Yahoo，跳過本地)
    ALL     // 全部 (混合搜尋)
}

@Singleton
class StockFetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("StockCachePrefs", Context.MODE_PRIVATE)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // 本地台股快取
    private var twStockCache: Map<String, String> = emptyMap()
    private var twStockMarketTypeCache: Map<String, String> = emptyMap()
    private val initMutex = Mutex()


    suspend fun preloadTwStocks() {
        ensureTwStocksLoaded()
    }

    private suspend fun ensureTwStocksLoaded() {
        if (twStockCache.isNotEmpty()) return

        initMutex.withLock {
            if (twStockCache.isEmpty()) {
                fetchTwStockMapAndType()
            }
        }
    }

    private suspend fun fetchTwStockMapAndType() = withContext(Dispatchers.IO) {
        val todayStr = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val lastFetchDate = prefs.getString("LAST_FETCH_DATE", "")

        // 今日已更新
        if (todayStr == lastFetchDate) {
            val isSuccess = loadFromLocalCache()
            if (isSuccess) {
                return@withContext // 讀取成功就提早結束，不走網路了
            }
        }

        val nameMap = mutableMapOf<String, String>()
        val typeMap = mutableMapOf<String, String>() // 紀錄是 .TW 還是 .TWO

        try {
            val listedJob = async { fetchTwseListed() }
            val otcJob = async { fetchTpexOtc() }

            val listedRes = listedJob.await()
            val otcRes = otcJob.await()

            // 處理上市
            listedRes.forEach { (code, name) ->
                nameMap[code] = name
                typeMap[code] = ".TW"
            }

            // 處理上櫃
            otcRes.forEach { (code, name) ->
                nameMap[code] = name
                typeMap[code] = ".TWO"
            }

            twStockCache = nameMap
            twStockMarketTypeCache = typeMap

            saveToLocalCache(nameMap, typeMap)
            prefs.edit { putString("LAST_FETCH_DATE", todayStr) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 搜尋主入口 (整合了確保載入邏輯)
     */
    suspend fun searchStocks(query: String, region: SearchRegion = SearchRegion.ALL): List<Pair<String, String>> {
        val q = query.trim().uppercase()
        if (q.isBlank()) return emptyList()

        val results = mutableListOf<Pair<String, String>>()

        // --- 策略 A: 台股模式 ---
        if (region == SearchRegion.TW || region == SearchRegion.ALL) {

            ensureTwStocksLoaded() // 🌟 確保資料庫已就緒

            if (twStockCache.isNotEmpty()) {
                val isNumeric = q.isNotEmpty() && q.all { it.isDigit() }
                val localResults = withContext(Dispatchers.Default) {
                    twStockCache.entries
                        .filter { (code, name) ->
                            code.startsWith(q) || name.contains(q)
                        }
                        .sortedByDescending { (code, _) ->
                            if (isNumeric) code.startsWith(q) else false
                        }
                        .take(20)
                        .map { (code, name) ->
                            val suffix = twStockMarketTypeCache[code] ?: ""
                            val finalCode = "$code$suffix"
                            finalCode to name
                        }
                }
                results.addAll(localResults)
            }

            if (region == SearchRegion.TW) return results
        }

        // --- 策略 B: 連網搜尋 (Yahoo 美股/ETF) ---
        if (region == SearchRegion.US || (region == SearchRegion.ALL && results.size < 5)) {
            val netResults = searchYahooRemote(q)

            val filteredNetResults = netResults.filter { (code, _) ->
                when (region) {
                    // 美股模式：嚴格過濾掉純數字 (台股)、結尾有 .HK, .SZ 等亞股
                    SearchRegion.US -> !code.all { it.isDigit() } && !code.contains(".")
                    else -> true
                }
            }

            val existingCodes = results.map { it.first }.toSet()
            filteredNetResults.forEach { item ->
                if (!existingCodes.contains(item.first)) {
                    results.add(item)
                }
            }
        }

        return results
    }

    // Yahoo 搜尋實作 (保持原本邏輯，只做微調)
    private suspend fun searchYahooRemote(query: String): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                // 如果是查美股，Yahoo 的搜尋建議通常很準
                val url = "https://query1.finance.yahoo.com/v1/finance/search?q=$query&lang=en-US&region=US&quotesCount=10"

                val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
                val response = client.newCall(request).execute()
                val jsonStr = response.body?.string() ?: return@withContext emptyList()

                val quotes = JSONObject(jsonStr).optJSONArray("quotes") ?: return@withContext emptyList()
                val list = mutableListOf<Pair<String, String>>()

                for (i in 0 until quotes.length()) {
                    val item = quotes.getJSONObject(i)
                    val symbol = item.optString("symbol")
                    val name = item.optString("shortname", item.optString("longname"))

                    // 簡單過濾：只顯示 Equity (股票) 和 ETF
                    val type = item.optString("quoteType")
                    if (type == "EQUITY" || type == "ETF") {
                        // 移除 .TW 後綴 (讓顯示一致)
                        val cleanSymbol = symbol.replace(".TW", "").replace(".TWO", "")
                        list.add(cleanSymbol to name)
                    }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 取得股票詳細報價
     */
    suspend fun fetchStockDetail(stockSymbol: String): StockDetail? {
        return withContext(Dispatchers.IO) {
            try {
                // 🌟 優化：利用剛才建好的快取，精準判斷是 .TW 還是 .TWO
                val yahooSymbol = when {
                    // 如果快取裡有記錄它是上櫃，就給 .TWO，上市就給 .TW
                    twStockMarketTypeCache.containsKey(stockSymbol) -> {
                        stockSymbol + twStockMarketTypeCache[stockSymbol]
                    }
                    // 如果使用者打的是純英文字母 (如 AAPL, TSLA)，就不加後綴 (當作美股)
                    stockSymbol.all { it.isLetter() } -> stockSymbol
                    // 如果快取找不到，但又是純數字，盲猜是上市 (.TW)
                    stockSymbol.all { it.isDigit() } -> "$stockSymbol.TW"
                    // 原封不動
                    else -> stockSymbol
                }

                val url = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol?interval=1d&range=2d"

                val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (bodyString != null) {
                        parseQuoteFromJson(bodyString)
                    } else null
                } else null

            } catch (e: Exception) {
                null
            }
        }
    }

    // 抓證交所 (上市)
    private fun fetchTwseListed(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val url = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONArray(response.body?.string())
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val code = item.optString("Code")
                    val name = item.optString("Name")
                    if (code.isNotEmpty()) map[code] = name
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return map
    }

    // 抓櫃買中心 (上櫃)
    private fun fetchTpexOtc(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val url = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONArray(response.body?.string())
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    // 櫃買中心的欄位名稱不同
                    val code = item.optString("SecuritiesCompanyCode")
                    val name = item.optString("CompanyName")
                    if (code.isNotEmpty()) map[code] = name
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return map
    }

    // quote API
    private fun parseQuoteFromJson(jsonStr: String?): StockDetail? {
        if (jsonStr.isNullOrEmpty()) return null

        return try {
            val jsonObject = JSONObject(jsonStr)
            val resultObj = jsonObject.getJSONObject("chart").getJSONArray("result").getJSONObject(0)

            // 1. 取得最新現價
            val meta = resultObj.getJSONObject("meta")
            val currentPrice = meta.getDouble("regularMarketPrice")

            // 2. 取得昨收價：優先使用 regularMarketPreviousClose，備案為 chartPreviousClose
            var previousClose = when {
                meta.has("regularMarketPreviousClose") -> meta.getDouble("regularMarketPreviousClose")
                meta.has("chartPreviousClose") -> meta.getDouble("chartPreviousClose")
                else -> 0.0
            }

            // 3. 防錯：若 meta 都沒提供，嘗試從 indicators 陣列抓取第一個有效收盤價
            if (previousClose <= 0) {
                try {
                    val closeArray = resultObj.getJSONObject("indicators")
                        .getJSONArray("quote")
                        .getJSONObject(0)
                        .getJSONArray("close")

                    for (i in 0 until closeArray.length()) {
                        val valAtI = closeArray.optDouble(i, 0.0)
                        if (valAtI > 0) {
                            previousClose = valAtI
                            break
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            // 如果最後還是沒拿到昨收，則將漲跌設為 0 (previousClose = currentPrice)
            if (previousClose <= 0) {
                previousClose = currentPrice
            }

            // 4. 手動計算漲跌與百分比 (避免直接使用 API 可能不準確的 regularMarketChange)
            val change = currentPrice - previousClose
            val changePercent = if (previousClose > 0) {
                (change / previousClose) * 100
            } else {
                0.0
            }

            StockDetail(
                currentPrice = currentPrice,
                change = change,
                changePercent = changePercent
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 將 Map 轉成 JSON 字串並存入 SharedPreferences
     */
    private fun saveToLocalCache(nameMap: Map<String, String>, typeMap: Map<String, String>) {
        val nameJson = JSONObject(nameMap).toString()
        val typeJson = JSONObject(typeMap).toString()

        prefs.edit()
            .putString("CACHE_NAMES", nameJson)
            .putString("CACHE_TYPES", typeJson)
            .apply()
    }

    /**
     * 從 SharedPreferences 讀出 JSON 字串並還原成 Map
     */
    private fun loadFromLocalCache(): Boolean {
        val nameJsonStr = prefs.getString("CACHE_NAMES", null)
        val typeJsonStr = prefs.getString("CACHE_TYPES", null)

        if (nameJsonStr == null || typeJsonStr == null) return false

        return try {
            val nameJson = JSONObject(nameJsonStr)
            val typeJson = JSONObject(typeJsonStr)

            val tempNameMap = mutableMapOf<String, String>()
            val tempTypeMap = mutableMapOf<String, String>()

            // 還原 Name Map
            nameJson.keys().forEach { key -> tempNameMap[key] = nameJson.getString(key) }
            // 還原 Type Map
            typeJson.keys().forEach { key -> tempTypeMap[key] = typeJson.getString(key) }

            // 塞回記憶體
            twStockCache = tempNameMap
            twStockMarketTypeCache = tempTypeMap
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}