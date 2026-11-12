package com.example.stock.core.data.repository

import com.example.stock.core.data.model.MarketAvailability
import com.example.stock.core.data.model.StockDetail
import com.example.stock.core.data.model.SearchRegion
import com.example.stock.core.data.source.TwStockLocalDataSource
import com.example.stock.core.data.source.TwStockRemoteDataSource
import com.example.stock.core.data.source.YahooRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val twLocal: TwStockLocalDataSource,
    private val twRemote: TwStockRemoteDataSource,
    private val yahoo: YahooRemoteDataSource,
    private val marketAvailability: MarketAvailability
) : StockRepository {

    // 記憶體快取 (代號 -> 名稱 / 市場後綴)
    private var twNames: Map<String, String> = emptyMap()
    private var twMarketTypes: Map<String, String> = emptyMap()
    private val initMutex = Mutex()

    override suspend fun preloadTwStocks() = ensureTwStocksLoaded()

    private suspend fun ensureTwStocksLoaded() {
        if (twNames.isNotEmpty()) return
        initMutex.withLock {
            if (twNames.isEmpty()) loadTwStocks()
        }
    }

    private suspend fun loadTwStocks() = withContext(Dispatchers.IO) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

        // 今天已更新過：直接讀本地快取
        if (twLocal.getLastFetchDate() == today) {
            twLocal.load()?.let {
                twNames = it.nameMap
                twMarketTypes = it.typeMap
                return@withContext
            }
        }

        val listedJob = async { twRemote.fetchTwseListed() }
        val otcJob = async { twRemote.fetchTpexOtc() }
        val listed = listedJob.await()
        val otc = otcJob.await()

        // 兩邊都抓不到（斷網）就不要存，也不要標記「今天已更新」
        if (listed.isEmpty() && otc.isEmpty()) return@withContext

        twNames = listed + otc
        twMarketTypes = listed.mapValues { ".TW" } + otc.mapValues { ".TWO" }

        twLocal.save(twNames, twMarketTypes)
        twLocal.setLastFetchDate(today)
    }

    override suspend fun searchStocks(
        query: String,
        region: SearchRegion
    ): List<Pair<String, String>> {
        val q = query.trim().uppercase()
        if (q.isBlank()) return emptyList()

        // 只使用這個 flavor 允許的地區：ALL 不被允許時退回台股；其他不被允許的地區直接回傳空結果
        val allowed = marketAvailability.searchRegions
        val effective = when {
            region in allowed -> region
            region == SearchRegion.ALL && SearchRegion.TW in allowed -> SearchRegion.TW
            else -> return emptyList()
        }

        val results = mutableListOf<Pair<String, String>>()

        if (effective == SearchRegion.TW || effective == SearchRegion.ALL) {
            ensureTwStocksLoaded()
            results += searchLocalTw(q)
            if (effective == SearchRegion.TW) return results
        }

        if (effective == SearchRegion.US || (effective == SearchRegion.ALL && results.size < 5)) {
            val remote = withContext(Dispatchers.IO) { yahoo.search(q) }
                .filter { (code, _) ->
                    if (effective == SearchRegion.US) !code.all { it.isDigit() } && !code.contains(".")
                    else true
                }
            val existing = results.map { it.first.substringBefore('.') }.toSet()
            results += remote.filter { it.first !in existing }
        }
        return results
    }

    private suspend fun searchLocalTw(q: String): List<Pair<String, String>> =
        withContext(Dispatchers.Default) {
            val isNumeric = q.all { it.isDigit() }
            twNames.entries
                .filter { (code, name) -> code.startsWith(q) || name.contains(q) }
                .sortedByDescending { (code, _) -> isNumeric && code.startsWith(q) }
                .take(20)
                .map { (code, name) -> "$code${twMarketTypes[code].orEmpty()}" to name }
        }

    override suspend fun fetchStockDetail(symbol: String): StockDetail? {
        ensureTwStocksLoaded() // 原本沒有這行，會導致上市/上櫃判斷失準
        val yahooSymbol = when {
            twMarketTypes.containsKey(symbol) -> symbol + twMarketTypes[symbol]
            symbol.all { it.isLetter() } -> symbol
            symbol.all { it.isDigit() } -> "$symbol.TW"
            else -> symbol
        }
        return withContext(Dispatchers.IO) { yahoo.fetchQuote(yahooSymbol) }
    }
}