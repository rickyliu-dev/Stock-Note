package com.example.stock.core.data.repository

import com.example.stock.core.data.source.SearchRegion
import com.example.stock.core.data.source.StockFetcher
import javax.inject.Inject

class StockRepositoryImpl @Inject constructor(
    private val stockFetcher: StockFetcher
) : StockRepository {

    override suspend fun searchStocks(
        query: String,
        region: SearchRegion
    ): List<Pair<String, String>> {
        return stockFetcher.searchStocks(query, region)
    }
}