package com.example.stock.core.data.repository

import com.example.stock.core.data.source.SearchRegion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface StockRepository {
    suspend fun searchStocks(
        query: String,
        region: SearchRegion = SearchRegion.ALL
    ): List<Pair<String, String>>
}