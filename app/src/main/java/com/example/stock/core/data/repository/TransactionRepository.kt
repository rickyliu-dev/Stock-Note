package com.example.stock.core.data.repository

import com.example.stock.core.data.dataClass.StockQuote
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.StockDetail
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.source.SearchRegion
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    val allAccounts: Flow<List<Account>>
    val transactionsDesc: Flow<List<TransactionItem>>
    val stockPricesFlow: Flow<Map<String, StockQuote>>

    suspend fun upsert(item: TransactionItem)
    suspend fun getTransactionItemById(id: Long): TransactionItem?
    suspend fun getPriceCache(): Map<String, StockQuote>
    suspend fun savePriceCache(priceMap: Map<String, StockQuote>)
    suspend fun preloadData()
    suspend fun searchStocks(query: String, region: SearchRegion = SearchRegion.TW): List<Pair<String, String>>
    suspend fun deleteTransactionById(id: Long)
    suspend fun deleteTransactionsByIds(ids: List<Long>)
    suspend fun upsertTransactions(items: List<TransactionItem>)
    suspend fun upsertAccount(account: Account)
    suspend fun deleteAccount(account: Account)
    fun getAccountFlowById(id: Long): Flow<Account?>
    suspend fun getAccountById(id: Long): Account?
    suspend fun importTransactionsToNewAccount(accountName: String, items: List<TransactionItem>): Long
    suspend fun fetchStockDetail(symbol: String): StockDetail?
}