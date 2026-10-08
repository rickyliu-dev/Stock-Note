package com.example.stock.core.domain

import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.data.repository.SettingsRepository.TwSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class GetStockInventoryUseCaseTest {

    private val calculator = mockk<FinancialCalculator>()
    private lateinit var useCase: GetStockInventoryUseCase
    private val noPreDeduct = TwSettings(showPreDeduct = false)

    @Before
    fun setUp() {
        every { calculator.isTaiwanEtf(any()) } returns false
        every { calculator.calculateTax(any(), any()) } returns 0.0
        every { calculator.calculateFee(any(), any(), any(), any()) } returns 0.0
        useCase = GetStockInventoryUseCase(calculator)
    }

    // ---------- 測試用的小工具（假設都集中在這裡，方便配合你的資料類別調整）----------

    private var nextId = 1L

    private fun tx(
        type: TransactionType,
        symbol: String = "2330",
        day: Int = 1,
        price: Double = 0.0,
        shares: Int = 0,
        fee: Double = 0.0,
        tax: Double = 0.0,
        total: Double = 0.0
    ) = TransactionItem(
        id = nextId++,
        accountId = 1L,
        type = type,
        symbol = symbol,
        name = "測試$symbol",
        price = price,
        shares = shares,
        date = LocalDate(2026, 1, day),
        fee = fee,
        tax = tax,
        note = "",
        dividend = 0.0,
        total = total,
        participatingShares = 0
    )

    private fun buy(shares: Int, price: Double, day: Int = 1, fee: Double = 0.0, symbol: String = "2330") =
        tx(TransactionType.BUY, symbol, day, price, shares, fee)

    private fun sell(
        shares: Int, price: Double, day: Int = 2,
        fee: Double = 0.0, tax: Double = 0.0, symbol: String = "2330"
    ) = tx(TransactionType.SELL, symbol, day, price, shares, fee, tax)

    private fun calculate(
        vararg trades: TransactionItem,
        method: CostBasisMethod = CostBasisMethod.AVERAGE_COST,
        quotes: Map<String, StockQuote> = emptyMap()
    ) = useCase(trades.toList(), quotes, noPreDeduct, method)

    private fun assertClose(expected: Double, actual: Double) =
        assertEquals(expected, actual, 0.0001)

    // ---------- 買進 ----------

    @Test
    fun `買進後持股數與平均成本含手續費`() {
        val r = calculate(buy(1000, 100.0, fee = 100.0))
        val p = r.positions.getValue("2330")

        assertClose(1000.0, p.shares)
        assertClose(100100.0, p.totalCost)
        assertClose(100.1, p.avgCost)
        assertClose(100.0, p.avgBuyPrice)          // 純買入均價不含手續費
        assertClose(-100100.0, r.totalCashFlow)
    }

    @Test
    fun `多次買進的平均成本`() {
        val r = calculate(buy(1000, 100.0, day = 1), buy(1000, 200.0, day = 2))
        val p = r.positions.getValue("2330")

        assertClose(2000.0, p.shares)
        assertClose(300000.0, p.totalCost)
        assertClose(150.0, p.avgCost)
        assertClose(150.0, p.avgBuyPrice)
    }

    // ---------- 賣出與已實現損益 ----------

    @Test
    fun `平均成本法賣出的已實現損益`() {
        val r = calculate(
            buy(1000, 100.0, day = 1),
            buy(1000, 200.0, day = 2),
            sell(1000, 300.0, day = 3)
        )
        val p = r.positions.getValue("2330")

        assertClose(150000.0, p.realizedProfit)     // 300000 - 150 * 1000
        assertClose(1000.0, p.shares)
        assertClose(150.0, p.avgCost)               // 剩下的均價不變
        assertClose(0.0, r.totalCashFlow)           // -100000 - 200000 + 300000
    }

    @Test
    fun `先進先出法賣出先消耗最早的批次`() {
        val r = calculate(
            buy(1000, 100.0, day = 1),
            buy(1000, 200.0, day = 2),
            sell(1000, 300.0, day = 3),
            method = CostBasisMethod.FIFO
        )
        val p = r.positions.getValue("2330")

        assertClose(200000.0, p.realizedProfit)     // 300000 - 100 * 1000
        assertClose(1000.0, p.shares)
        assertClose(200.0, p.avgCost)               // 剩下的是後買的那批
    }

    @Test
    fun `賣出使用交易紀錄中儲存的稅金`() {
        val r = calculate(
            buy(1000, 100.0),
            sell(1000, 110.0, fee = 100.0, tax = 330.0)
        )
        // 110000 - 100 - 330 - 100000
        assertClose(9570.0, r.positions.getValue("2330").realizedProfit)
    }

    @Test
    fun `稅金為 0 的舊資料改由 calculator 補算`() {
        every { calculator.calculateTax(110000.0, false) } returns 330.0

        val r = calculate(
            buy(1000, 100.0),
            sell(1000, 110.0, fee = 100.0, tax = 0.0)
        )

        assertClose(9570.0, r.positions.getValue("2330").realizedProfit)
        verify { calculator.calculateTax(110000.0, false) }
    }

    @Test
    fun `賣出超過持股時持股不會變成負數`() {
        val r = calculate(buy(1000, 100.0), sell(1500, 110.0))
        val p = r.positions.getValue("2330")

        assertClose(0.0, p.shares)
        assertClose(1000.0, p.sellShares)           // 只計入實際賣出的股數
    }

    @Test
    fun `已清倉的股票不計未實現損益`() {
        val quotes = mapOf("2330" to StockQuote(currentPrice = 120.0, change = 5.0, changePercent = 4.3))
        val r = calculate(buy(1000, 100.0), sell(1000, 110.0), quotes = quotes)
        val p = r.positions.getValue("2330")

        assertClose(0.0, p.shares)
        assertClose(0.0, p.avgCost)
        assertClose(0.0, p.unrealizedProfit)
        assertClose(0.0, p.dailyProfit)
        assertClose(10000.0, p.realizedProfit)
    }

    @Test
    fun `交易依日期排序而不是依輸入順序`() {
        // 賣出排在前面，但日期較晚
        val r = calculate(sell(500, 110.0, day = 2), buy(1000, 100.0, day = 1))
        val p = r.positions.getValue("2330")

        assertClose(5000.0, p.realizedProfit)       // 55000 - 50000
        assertClose(500.0, p.shares)
    }

    @Test
    fun `多檔股票的已實現損益分開計算再加總`() {
        val r = calculate(
            buy(1000, 100.0, day = 1, symbol = "2330"),
            sell(1000, 110.0, day = 2, symbol = "2330"),   // +10000
            buy(1000, 50.0, day = 1, symbol = "2317"),
            sell(1000, 40.0, day = 2, symbol = "2317")     // -10000
        )

        assertClose(10000.0, r.positions.getValue("2330").realizedProfit)
        assertClose(-10000.0, r.positions.getValue("2317").realizedProfit)
        assertClose(0.0, r.totalRealizedProfit)
    }

    // ---------- 現金與股利 ----------

    @Test
    fun `存款提款調整只影響現金流`() {
        val r = calculate(
            tx(TransactionType.DEPOSIT, symbol = "", total = 100000.0),
            tx(TransactionType.WITHDRAW, symbol = "", total = 30000.0),
            tx(TransactionType.ADJUSTMENT, symbol = "", total = -500.0)
        )
        assertClose(69500.0, r.totalCashFlow)
    }

    @Ignore("已知問題：純現金交易會產生空的持股項目，等現金功能啟用時修正")
    @Test
    fun `只有現金交易時不應產生持股項目`() {
        val r = calculate(tx(TransactionType.DEPOSIT, symbol = "", total = 100000.0))
        assertTrue(r.positions.isEmpty())           // 目前預期會失敗，見上面的 bug 說明
    }

    @Test
    fun `現金股利以淨額計入股利與現金流`() {
        val quotes = mapOf("2330" to StockQuote(currentPrice = 100.0, change = 0.0, changePercent = 0.0))
        val r = calculate(
            buy(1000, 100.0),
            tx(TransactionType.DIVIDEND, day = 2, total = 950.0),
            quotes = quotes
        )
        val p = r.positions.getValue("2330")

        assertClose(950.0, p.dividendAmount)
        assertClose(-99050.0, r.totalCashFlow)      // -100000 + 950
        assertClose(950.0, p.totalProfit)           // 未實現 0 + 已實現 0 + 股利
    }

    // ---------- 股票股利、分割、減資 ----------

    @Test
    fun `股票股利增加股數但不增加成本與買入均價`() {
        val r = calculate(
            buy(1000, 100.0),
            tx(TransactionType.STOCK_DIVIDEND, day = 2, shares = 100)
        )
        val p = r.positions.getValue("2330")

        assertClose(1100.0, p.shares)
        assertClose(100000.0, p.totalCost)
        assertClose(100000.0 / 1100, p.avgCost)
        assertClose(100.0, p.avgBuyPrice)           // 無償配股不影響純買入均價
    }

    @Test
    fun `分割後平均成本等比例下降`() {
        val r = calculate(
            buy(1000, 100.0),
            tx(TransactionType.SPLIT, day = 2, shares = 1000)   // 1 拆 2
        )
        val p = r.positions.getValue("2330")

        assertClose(2000.0, p.shares)
        assertClose(100000.0, p.totalCost)
        assertClose(50.0, p.avgCost)
    }

    @Test
    fun `先進先出法分割後賣出以調整後的批次成本計算`() {
        val r = calculate(
            buy(1000, 100.0, day = 1),
            tx(TransactionType.SPLIT, day = 2, shares = 1000),
            sell(1000, 60.0, day = 3),
            method = CostBasisMethod.FIFO
        )
        val p = r.positions.getValue("2330")

        assertClose(10000.0, p.realizedProfit)      // 60000 - 50 * 1000
        assertClose(50.0, p.avgCost)
    }

    @Test
    fun `減資退還現金少於成本時沖減成本`() {
        val r = calculate(
            buy(1000, 100.0),
            tx(TransactionType.CAPITAL_REDUCTION, day = 2, shares = -500, total = 20000.0)
        )
        val p = r.positions.getValue("2330")

        assertClose(500.0, p.shares)
        assertClose(80000.0, p.totalCost)
        assertClose(160.0, p.avgCost)
        assertClose(0.0, p.realizedProfit)
        assertClose(-80000.0, r.totalCashFlow)      // -100000 + 20000
    }

    @Test
    fun `減資退還現金超過成本時超出部分列為已實現損益`() {
        val r = calculate(
            buy(1000, 100.0),
            tx(TransactionType.CAPITAL_REDUCTION, day = 2, shares = -500, total = 150000.0)
        )
        val p = r.positions.getValue("2330")

        assertClose(50000.0, p.realizedProfit)      // 150000 - 100000
        assertClose(0.0, p.totalCost)
        assertClose(500.0, p.shares)
    }

    // ---------- 報價、未實現與當日損益 ----------

    @Test
    fun `未實現與當日損益依報價計算`() {
        val quotes = mapOf("2330" to StockQuote(currentPrice = 120.0, change = 5.0, changePercent = 4.3478))
        val r = calculate(buy(1000, 100.0), quotes = quotes)
        val p = r.positions.getValue("2330")

        assertClose(115.0, p.yesterdayPrice)
        assertClose(120000.0, p.marketValue)
        assertClose(20000.0, p.unrealizedProfit)    // 120000 - 100000
        assertClose(5000.0, p.dailyProfit)          // 120000 - 115000
        assertClose(20000.0, r.portfolioUnrealizedProfit)
        assertClose(5000.0, r.portfolioDailyProfit)
    }

    @Test
    fun `開啟預扣時損益改用扣除手續費與稅後的淨市值`() {
        every { calculator.calculateFee(any(), any(), any(), any()) } returns 100.0
        every { calculator.calculateTax(any(), any()) } returns 360.0
        val quotes = mapOf("2330" to StockQuote(currentPrice = 120.0, change = 5.0, changePercent = 4.3478))

        val r = useCase(
            listOf(buy(1000, 100.0)),
            quotes,
            TwSettings(showPreDeduct = true),
            CostBasisMethod.AVERAGE_COST
        )
        val p = r.positions.getValue("2330")

        assertClose(119540.0, p.netMarketValue)     // 120000 - 100 - 360
        assertClose(19540.0, p.unrealizedProfit)    // 119540 - 100000
        assertClose(5000.0, p.dailyProfit)          // 119540 - 114540
    }
}