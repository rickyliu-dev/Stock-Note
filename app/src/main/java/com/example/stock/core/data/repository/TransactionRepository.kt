package com.example.stock.core.data.repository

import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.StockPriceEntity
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionMapper
import com.example.stock.core.data.model.TransactionDao
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
class TransactionRepository @Inject constructor(
    private val stockFetcher: StockFetcher,
    private val transactionDao: TransactionDao
) {
    // 取得所有帳戶
    val allAccounts: Flow<List<Account>> = transactionDao.getAllAccounts()

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
    val transactionsDesc: Flow<List<TransactionItem>> = transactionDao.getAllTransactions()
        .map { list -> list.map { TransactionMapper.toItem(it) } }

    val stockPricesFlow: Flow<Map<String, StockQuote>> = transactionDao.getAllCachedPricesFlow()
        .map { entities ->
            entities.associate { entity ->
                entity.symbol to StockQuote(
                    currentPrice = entity.price,
                    change = entity.change,
                    changePercent = entity.changePercent
                )
            }
        }

    suspend fun upsert(item: TransactionItem) {
        transactionDao.upsertTransaction(TransactionMapper.toEntity(item))
    }

    suspend fun getTransactionItemById(id: Long): TransactionItem? {
        return transactionDao.getTransactionById(id)?.let { TransactionMapper.toItem(it) }
    }

    // 1. 讀取快取：把 List<Entity> 轉成 Map 方便 UI 使用
    suspend fun getPriceCache(): Map<String, StockQuote> {
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
    suspend fun savePriceCache(priceMap: Map<String, StockQuote>) {
        val entities = priceMap.map { (symbol, stockQuote) ->
            StockPriceEntity(symbol = symbol, price = stockQuote.currentPrice, change = stockQuote.change, changePercent = stockQuote.changePercent)
        }
        transactionDao.insertStockPrices(entities)
    }

    suspend fun preloadData() {
        stockFetcher.preloadTwStocks()
    }

    suspend fun searchStocks(query: String, region: SearchRegion = SearchRegion.TW): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()

        return stockFetcher.searchStocks(query, region)
    }

    suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun deleteTransactionsByIds(ids: List<Long>) {
        transactionDao.deleteByIds(ids)
    }

    suspend fun upsertTransactions(items: List<TransactionItem>) {
        transactionDao.upsertTransactions(items.map { TransactionMapper.toEntity(it) })
    }

    suspend fun upsertAccount(account: Account) {
        transactionDao.upsertAccount(account)
    }

    suspend fun deleteAccount(account: Account) {
        transactionDao.deleteAccount(account)
    }

    fun getAccountFlowById(id: Long): Flow<Account?> {
        return transactionDao.getAccountFlowById(id)
    }

    suspend fun getAccountById(id: Long): Account? {
        return transactionDao.getAccountById(id)
    }
}