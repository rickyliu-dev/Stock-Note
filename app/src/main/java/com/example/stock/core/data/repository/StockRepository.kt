package com.example.stock.core.data.repository

import com.example.stock.core.data.model.StockDetail
import com.example.stock.core.data.model.SearchRegion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface StockRepository {
    suspend fun preloadTwStocks()
    suspend fun searchStocks(
        query: String,
        region: SearchRegion = SearchRegion.TW // 預設TW
    ): List<Pair<String, String>>
    suspend fun fetchStockDetail(symbol: String): StockDetail?
}