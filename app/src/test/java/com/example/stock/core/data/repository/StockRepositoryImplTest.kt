package com.example.stock.core.data.repository

import com.example.stock.core.data.model.StockDetail
import com.example.stock.core.data.model.SearchRegion
import com.example.stock.core.data.source.TwStockCacheData
import com.example.stock.core.data.source.TwStockLocalDataSource
import com.example.stock.core.data.source.TwStockRemoteDataSource
import com.example.stock.core.data.source.YahooRemoteDataSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StockRepositoryImplTest {

    private val twLocal = mockk<TwStockLocalDataSource>()
    private val twRemote = mockk<TwStockRemoteDataSource>()
    private val yahoo = mockk<YahooRemoteDataSource>()

    // 注意：0050 排在 5009 前面，用來驗證「代號開頭符合」會被排到前面
    private val listed = linkedMapOf(
        "0050" to "元大台灣50",
        "5009" to "榮剛",
        "2330" to "台積電",
        "2317" to "鴻海",
        "2303" to "聯電"
    )
    private val otc = linkedMapOf(
        "6488" to "環球晶",
        "3293" to "鈊象"
    )

    private lateinit var repo: StockRepositoryImpl

    @Before
    fun setUp() {
        every { twLocal.getLastFetchDate() } returns ""
        every { twLocal.save(any(), any()) } just runs
        every { twLocal.setLastFetchDate(any()) } just runs
        every { twRemote.fetchTwseListed() } returns listed
        every { twRemote.fetchTpexOtc() } returns otc
        every { yahoo.search(any()) } returns emptyList()
        every { yahoo.fetchQuote(any()) } returns StockDetail(100.0, 1.0, 1.0)

        repo = StockRepositoryImpl(twLocal, twRemote, yahoo)
    }

    private fun codes(result: List<Pair<String, String>>) = result.map { it.first }

    // ---------- 台股搜尋 ----------

    @Test
    fun `搜尋代號開頭，回傳帶市場後綴的結果`() = runTest {
        val result = repo.searchStocks("23", SearchRegion.TW)
        assertEquals(setOf("2330.TW", "2317.TW", "2303.TW"), codes(result).toSet())
    }

    @Test
    fun `搜尋中文名稱`() = runTest {
        val result = repo.searchStocks("台積", SearchRegion.TW)
        assertEquals(listOf("2330.TW" to "台積電"), result)
    }

    @Test
    fun `上櫃股票後綴為 TWO`() = runTest {
        val result = repo.searchStocks("6488", SearchRegion.TW)
        assertEquals(listOf("6488.TWO" to "環球晶"), result)
    }

    @Test
    fun `純數字查詢時，代號開頭符合的排在名稱符合的前面`() = runTest {
        // "50" 同時符合 5009（代號開頭）和 0050（名稱含 50）
        val result = repo.searchStocks("50", SearchRegion.TW)
        assertEquals("5009.TW", result.first().first)
        assertTrue(codes(result).contains("0050.TW"))
    }

    @Test
    fun `查詢字串會 trim 並轉大寫`() = runTest {
        val result = repo.searchStocks("  2330  ", SearchRegion.TW)
        assertEquals(listOf("2330.TW"), codes(result))
    }

    @Test
    fun `空白查詢回傳空清單且不打任何網路`() = runTest {
        val result = repo.searchStocks("   ", SearchRegion.ALL)
        assertTrue(result.isEmpty())
        verify(exactly = 0) { twRemote.fetchTwseListed() }
        verify(exactly = 0) { yahoo.search(any()) }
    }

    @Test
    fun `結果最多 20 筆`() = runTest {
        every { twRemote.fetchTwseListed() } returns
                (1..30).associate { "1%03d".format(it) to "測試$it" }
        val result = repo.searchStocks("1", SearchRegion.TW)
        assertEquals(20, result.size)
    }

    // ---------- 區域策略 ----------

    @Test
    fun `TW 模式不會呼叫 Yahoo`() = runTest {
        repo.searchStocks("2330", SearchRegion.TW)
        verify(exactly = 0) { yahoo.search(any()) }
    }

    @Test
    fun `US 模式過濾掉純數字與帶點的代號`() = runTest {
        every { yahoo.search("A") } returns listOf(
            "AAPL" to "Apple",
            "2330" to "TSMC",
            "0700.HK" to "Tencent"
        )
        val result = repo.searchStocks("A", SearchRegion.US)
        assertEquals(listOf("AAPL" to "Apple"), result)
        verify(exactly = 0) { twRemote.fetchTwseListed() }
    }

    @Test
    fun `ALL 模式本地結果不足 5 筆時會補查 Yahoo`() = runTest {
        every { yahoo.search("2330") } returns listOf("TSM" to "TSMC ADR")
        val result = repo.searchStocks("2330", SearchRegion.ALL)
        assertEquals(listOf("2330.TW", "TSM"), codes(result))
    }

    @Test
    fun `ALL 模式 Yahoo 回傳的台股代號不會與本地結果重複`() = runTest {
        // Yahoo 已把 .TW 後綴移除，所以回來的是 "2330"
        every { yahoo.search("2330") } returns listOf("2330" to "TSMC", "TSM" to "TSMC ADR")
        val result = repo.searchStocks("2330", SearchRegion.ALL)
        assertEquals(listOf("2330.TW", "TSM"), codes(result))
    }

    @Test
    fun `ALL 模式本地結果達 5 筆時不查 Yahoo`() = runTest {
        every { twRemote.fetchTwseListed() } returns
                (1..6).associate { "100$it" to "測試$it" }
        repo.searchStocks("100", SearchRegion.ALL)
        verify(exactly = 0) { yahoo.search(any()) }
    }

    // ---------- 查詢報價：Yahoo 代號判斷 ----------

    @Test
    fun `上市股票以 TW 後綴查報價`() = runTest {
        repo.fetchStockDetail("2330")
        verify { yahoo.fetchQuote("2330.TW") }
    }

    @Test
    fun `上櫃股票以 TWO 後綴查報價`() = runTest {
        repo.fetchStockDetail("6488")
        verify { yahoo.fetchQuote("6488.TWO") }
    }

    @Test
    fun `純英文代號視為美股，不加後綴`() = runTest {
        repo.fetchStockDetail("AAPL")
        verify { yahoo.fetchQuote("AAPL") }
    }

    @Test
    fun `快取找不到的純數字代號預設當上市`() = runTest {
        repo.fetchStockDetail("9999")
        verify { yahoo.fetchQuote("9999.TW") }
    }

    @Test
    fun `其他格式的代號原封不動`() = runTest {
        repo.fetchStockDetail("BRK-B")
        verify { yahoo.fetchQuote("BRK-B") }
    }

    // ---------- 快取行為 ----------

    @Test
    fun `今天已更新過就讀本地快取，不打網路`() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        every { twLocal.getLastFetchDate() } returns today
        every { twLocal.load() } returns TwStockCacheData(
            nameMap = mapOf("2330" to "台積電"),
            typeMap = mapOf("2330" to ".TW")
        )

        val result = repo.searchStocks("2330", SearchRegion.TW)

        assertEquals(listOf("2330.TW"), codes(result))
        verify(exactly = 0) { twRemote.fetchTwseListed() }
        verify(exactly = 0) { twRemote.fetchTpexOtc() }
    }

    @Test
    fun `斷網抓不到資料時不寫入快取也不標記今天已更新`() = runTest {
        every { twRemote.fetchTwseListed() } returns emptyMap()
        every { twRemote.fetchTpexOtc() } returns emptyMap()

        val result = repo.searchStocks("2330", SearchRegion.TW)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { twLocal.save(any(), any()) }
        verify(exactly = 0) { twLocal.setLastFetchDate(any()) }
    }

    @Test
    fun `成功抓到資料後會存快取並標記日期`() = runTest {
        repo.preloadTwStocks()
        verify(exactly = 1) { twLocal.save(any(), any()) }
        verify(exactly = 1) { twLocal.setLastFetchDate(any()) }
    }

    @Test
    fun `重複呼叫只會載入一次`() = runTest {
        repo.searchStocks("2330", SearchRegion.TW)
        repo.searchStocks("2317", SearchRegion.TW)
        repo.fetchStockDetail("2330")
        verify(exactly = 1) { twRemote.fetchTwseListed() }
    }
}