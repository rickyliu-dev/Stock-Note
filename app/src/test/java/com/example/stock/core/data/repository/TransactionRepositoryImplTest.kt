package com.example.stock.core.data.repository

import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.StockPriceEntity
import com.example.stock.core.data.model.Transaction
import com.example.stock.core.data.model.TransactionDao
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryImplTest {

    private val dao = mockk<TransactionDao>(relaxed = true)

    @Before
    fun setUp() {
        every { dao.getAllAccounts() } returns flowOf(emptyList())
        every { dao.getAllTransactions() } returns flowOf(emptyList())
        every { dao.getAllCachedPricesFlow() } returns flowOf(emptyList())
    }

    private fun TestScope.createRepo() = TransactionRepositoryImpl(
        transactionDao = dao,
        appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    // ---------- 預設帳戶 ----------

    @Test
    fun `沒有任何帳戶時建立預設帳戶`() = runTest {
        createRepo()
        coVerify(exactly = 1) {
            dao.upsertAccount(match { it.id == 1L && it.name == "預設帳戶" && it.currency == "TWD" })
        }
    }

    @Test
    fun `已有帳戶時不建立預設帳戶`() = runTest {
        every { dao.getAllAccounts() } returns
                flowOf(listOf(Account(id = 7L, name = "我的帳戶", currency = "TWD")))
        createRepo()
        coVerify(exactly = 0) { dao.upsertAccount(any()) }
    }

    // ---------- 報價快取轉換 ----------

    @Test
    fun `stockPricesFlow 把 Entity 轉成以代號為 key 的報價`() = runTest {
        every { dao.getAllCachedPricesFlow() } returns flowOf(
            listOf(
                StockPriceEntity(symbol = "2330", price = 1000.0, change = 10.0, changePercent = 1.0),
                StockPriceEntity(symbol = "AAPL", price = 200.0, change = -2.0, changePercent = -1.0)
            )
        )
        val prices = createRepo().stockPricesFlow.first()

        assertEquals(setOf("2330", "AAPL"), prices.keys)
        assertEquals(1000.0, prices.getValue("2330").currentPrice, 0.0001)
        assertEquals(-2.0, prices.getValue("AAPL").change, 0.0001)
        assertEquals(-1.0, prices.getValue("AAPL").changePercent, 0.0001)
    }

    @Test
    fun `getPriceCache 把 Entity 轉成 Map`() = runTest {
        coEvery { dao.getAllCachedPrices() } returns listOf(
            StockPriceEntity(symbol = "2330", price = 1000.0, change = 10.0, changePercent = 1.0)
        )
        val cache = createRepo().getPriceCache()

        assertEquals(1, cache.size)
        assertEquals(1000.0, cache.getValue("2330").currentPrice, 0.0001)
    }

    @Test
    fun `savePriceCache 把報價 Map 轉回 Entity 存入`() = runTest {
        val repo = createRepo()
        repo.savePriceCache(
            mapOf("2330" to StockQuote(currentPrice = 1000.0, change = 10.0, changePercent = 1.0))
        )

        val saved = slot<List<StockPriceEntity>>()
        coVerify { dao.insertStockPrices(capture(saved)) }
        assertEquals(1, saved.captured.size)
        assertEquals("2330", saved.captured[0].symbol)
        assertEquals(1000.0, saved.captured[0].price, 0.0001)
        assertEquals(10.0, saved.captured[0].change, 0.0001)
    }

    // ---------- 匯入 ----------

    @Test
    fun `匯入會用指定名稱與 TWD 建立新帳戶並回傳其 id`() = runTest {
        coEvery { dao.insertAccount(any()) } returns 5L

        val id = createRepo().importTransactionsToNewAccount("匯入帳戶", emptyList())

        assertEquals(5L, id)
        coVerify { dao.insertAccount(match { it.name == "匯入帳戶" && it.currency == "TWD" }) }
    }

    // ---------- 匯入到新帳戶 ----------

    private fun sampleItem(accountId: Long, symbol: String) = TransactionItem(
        id = 0L,
        accountId = accountId,
        type = TransactionType.BUY,
        symbol = symbol,
        name = "測試",
        price = 100.0,
        shares = 1000,
        date = LocalDate(2026, 1, 2),
        fee = 0.0,
        tax = 0.0,
        note = "",
        dividend = 0.0,
        total = 100000.0,
        participatingShares = 0
    )

    @Test
    fun `匯入時用傳入名稱建立新帳戶，每筆交易都改指派到新帳戶 id`() = runTest {
        val newId = 42L
        val accountSlot = slot<Account>()
        coEvery { dao.insertAccount(capture(accountSlot)) } returns newId

        val items = listOf(
            sampleItem(accountId = 1L, symbol = "2330"),
            sampleItem(accountId = 5L, symbol = "AAPL")
        )

        val result = createRepo().importTransactionsToNewAccount("匯入帳戶", items)

        assertEquals(newId, result)
        assertEquals("匯入帳戶", accountSlot.captured.name)
        assertEquals("TWD", accountSlot.captured.currency)

        val savedSlot = slot<List<Transaction>>()
        coVerify(exactly = 1) { dao.upsertTransactions(capture(savedSlot)) }
        assertEquals(2, savedSlot.captured.size)
        assertTrue(savedSlot.captured.all { it.accountId == newId })
        assertEquals(listOf("2330", "AAPL"), savedSlot.captured.map { it.symbol })
    }
}