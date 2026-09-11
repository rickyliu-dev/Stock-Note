package com.example.stock.core.data.repository

import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.StockDetail
import com.example.stock.core.data.model.StockPriceEntity
import com.example.stock.core.data.model.TransactionDao
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionMapper
import com.example.stock.core.data.source.SearchRegion
import com.example.stock.core.data.source.StockFetcher
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val stockFetcher: StockFetcher,
    private val transactionDao: TransactionDao
) : TransactionRepository {
    // 取得所有帳戶
    override val allAccounts: Flow<List<Account>> = transactionDao.getAllAccounts()

    init {
        // 初始化檢查：如果沒有帳戶，建立一個預設帳戶
        MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
            val accounts = transactionDao.getAllAccounts().first()
            if (accounts.isEmpty()) {
                transactionDao.upsertAccount(
                    Account(
                        id = 1L,
                        name = "預設帳戶",
                        currency = "TWD"
                    )
                )
            }
        }
    }

    // 取得所有交易記錄，並透過 Mapper 轉為 UI Model
    override val transactionsDesc: Flow<List<TransactionItem>> = transactionDao.getAllTransactions()
        .map { list -> list.map { TransactionMapper.toItem(it) } }

    override val stockPricesFlow: Flow<Map<String, StockQuote>> = transactionDao.getAllCachedPricesFlow()
        .map { entities ->
            entities.associate { entity ->
                entity.symbol to StockQuote(
                    currentPrice = entity.price,
                    change = entity.change,
                    changePercent = entity.changePercent
                )
            }
        }

    override suspend fun upsert(item: TransactionItem) {
        transactionDao.upsertTransaction(TransactionMapper.toEntity(item))
    }

    override suspend fun getTransactionItemById(id: Long): TransactionItem? {
        return transactionDao.getTransactionById(id)?.let { TransactionMapper.toItem(it) }
    }

    // 1. 讀取快取：把 List<Entity> 轉成 Map 方便 UI 使用
    override suspend fun getPriceCache(): Map<String, StockQuote> {
        return transactionDao.getAllCachedPrices().associate { entity ->
            val quote = StockQuote(
                currentPrice = entity.price,
                change = entity.change,
                changePercent = entity.changePercent
            )
            entity.symbol to quote
        }
    }

    // 2. 儲存快取：把 Map 轉回 Entity 存入資料庫
    override suspend fun savePriceCache(priceMap: Map<String, StockQuote>) {
        val entities = priceMap.map { (symbol, stockQuote) ->
            StockPriceEntity(symbol = symbol, price = stockQuote.currentPrice, change = stockQuote.change, changePercent = stockQuote.changePercent)
        }
        transactionDao.insertStockPrices(entities)
    }

    override suspend fun preloadData() {
        stockFetcher.preloadTwStocks()
    }

    override suspend fun searchStocks(query: String, region: SearchRegion): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()

        return stockFetcher.searchStocks(query, region)
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteById(id)
    }

    override suspend fun deleteTransactionsByIds(ids: List<Long>) {
        transactionDao.deleteByIds(ids)
    }

    override suspend fun upsertTransactions(items: List<TransactionItem>) {
        transactionDao.upsertTransactions(items.map { TransactionMapper.toEntity(it) })
    }

    override suspend fun upsertAccount(account: Account) {
        transactionDao.upsertAccount(account)
    }

    override suspend fun deleteAccount(account: Account) {
        transactionDao.deleteAccount(account)
    }

    override fun getAccountFlowById(id: Long): Flow<Account?> {
        return transactionDao.getAccountFlowById(id)
    }

    override suspend fun getAccountById(id: Long): Account? {
        return transactionDao.getAccountById(id)
    }

    override suspend fun importTransactionsToNewAccount(accountName: String, items: List<TransactionItem>): Long {
        val newAccountId = transactionDao.insertAccount(
            Account(
                name = accountName,
                currency = "TWD"
            )
        )

        val entities = items.map {
            TransactionMapper.toEntity(it.copy(accountId = newAccountId))
        }
        transactionDao.upsertTransactions(entities)
        return newAccountId
    }

    override suspend fun fetchStockDetail(symbol: String): StockDetail? {
        return stockFetcher.fetchStockDetail(symbol)
    }
}