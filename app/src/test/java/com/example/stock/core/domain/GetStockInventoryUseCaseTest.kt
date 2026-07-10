package com.example.stock.core.domain

import com.example.stock.core.data.TaiwanFinancialCalculator
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.domain.model.StockPosition
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetStockInventoryUseCaseTest {

    private lateinit var useCase: GetStockInventoryUseCase
    private val financialCalculator = TaiwanFinancialCalculator()

    @Before
    fun setup() {
        useCase = GetStockInventoryUseCase(financialCalculator)
    }

    @Test
    fun `test basic buy and sell inventory`() {
        val transactions = listOf(
            createTransaction(1, TransactionType.BUY, "2330", "TSMC", 500.0, 1000, fee = 712.0, date = LocalDate(2023, 1, 1)),
            createTransaction(2, TransactionType.SELL, "2330", "TSMC", 600.0, 500, fee = 427.0, date = LocalDate(2023, 1, 2))
        )

        val result = useCase(transactions)
        val position = result.positions["2330"]!!

        // Buy: 500 * 1000 + 712 = 500712
        // Sell: 600 * 500 - 427 - tax(floor(600*500*0.003)=900) = 300000 - 427 - 900 = 298673
        // Shares: 1000 - 500 = 500
        // Avg Cost (Average Cost Method): 500712 / 1000 = 500.712
        // Cost of Goods Sold: (500712 / 1000) * 500 = 250356.0
        // Realized Profit: 298673 - 250356 = 48317.0
        // Remaining Cost: 500712 - 250356 = 250356.0

        assertEquals(500.0, position.shares, 0.001)
        assertEquals(250356.0, position.totalCost, 0.001)
        assertEquals(48317.0, position.realizedProfit, 0.001)
    }

    @Test
    fun `test stock dividend calculation`() {
        val transactions = listOf(
            createTransaction(1, TransactionType.BUY, "2330", "TSMC", 500.0, 1000, fee = 712.0, date = LocalDate(2023, 1, 1)),
            // 1000 shares, stock dividend 100 shares (delta)
            createTransaction(2, TransactionType.STOCK_DIVIDEND, "2330", "TSMC", 0.0, 100, date = LocalDate(2023, 1, 2), participatingShares = 1000)
        )

        val result = useCase(transactions)
        val position = result.positions["2330"]!!

        assertEquals(1100.0, position.shares, 0.001)
        assertEquals(500712.0, position.totalCost, 0.001)
        assertEquals(500712.0 / 1100, position.avgCost, 0.001)
    }

    @Test
    fun `test split calculation`() {
        val transactions = listOf(
            createTransaction(1, TransactionType.BUY, "AAPL", "Apple", 150.0, 10, fee = 0.0, date = LocalDate(2023, 1, 1)),
            // 1-to-4 split means +30 shares if we had 10
            createTransaction(2, TransactionType.SPLIT, "AAPL", "Apple", 0.0, 30, date = LocalDate(2023, 1, 2), participatingShares = 10)
        )

        val result = useCase(transactions)
        val position = result.positions["AAPL"]!!

        assertEquals(40.0, position.shares, 0.001)
        assertEquals(1500.0, position.totalCost, 0.001)
        assertEquals(1500.0 / 40, position.avgCost, 0.001)
    }

    @Test
    fun `test capital reduction calculation`() {
        val transactions = listOf(
            createTransaction(1, TransactionType.BUY, "2303", "UMC", 50.0, 1000, fee = 0.0, date = LocalDate(2023, 1, 1)),
            // 20% reduction: refund 2.0 per share, shares reduce by 200
            // total = 1000 * 2.0 = 2000 refund
            createTransaction(2, TransactionType.CAPITAL_REDUCTION, "2303", "UMC", 2.0, -200, total = 2000.0, date = LocalDate(2023, 1, 2), participatingShares = 1000)
        )

        val result = useCase(transactions)
        val position = result.positions["2303"]!!

        assertEquals(800.0, position.shares, 0.001)
        assertEquals(48000.0, position.totalCost, 0.001)
        assertEquals(60.0, position.avgCost, 0.001)
    }

    @Test
    fun `test fifo cost basis`() {
        val transactions = listOf(
            createTransaction(1, TransactionType.BUY, "2330", "TSMC", 500.0, 1000, fee = 0.0, date = LocalDate(2023, 1, 1)), // Cost 500k
            createTransaction(2, TransactionType.BUY, "2330", "TSMC", 600.0, 1000, fee = 0.0, date = LocalDate(2023, 1, 2)), // Cost 600k
            createTransaction(3, TransactionType.SELL, "2330", "TSMC", 550.0, 1000, fee = 0.0, date = LocalDate(2023, 1, 3)) // Sells the first 1000
        )

        // FIFO:
        // Sell 1000: uses first lot (500k cost)
        // Revenue: 1000 * 550 - tax(floor(550k * 0.003) = 1650) = 548350
        // Profit: 548350 - 500000 = 48350
        // Remaining: 1000 shares from second lot, cost 600000

        val result = useCase(allTransactions = transactions, method = CostBasisMethod.FIFO)
        val position = result.positions["2330"]!!

        assertEquals(1000.0, position.shares, 0.001)
        assertEquals(600000.0, position.totalCost, 0.001)
        assertEquals(48350.0, position.realizedProfit, 0.001)
    }

    private fun createTransaction(
        id: Long,
        type: TransactionType,
        symbol: String,
        name: String,
        price: Double,
        shares: Int,
        fee: Double = 0.0,
        total: Double = 0.0,
        date: LocalDate = LocalDate(2024, 1, 1),
        participatingShares: Int = 0
    ) = TransactionItem(
        id = id,
        type = type,
        symbol = symbol,
        name = name,
        price = price,
        shares = shares,
        date = date,
        fee = fee,
        note = "",
        dividend = 0.0,
        total = total,
        participatingShares = participatingShares
    )
}
