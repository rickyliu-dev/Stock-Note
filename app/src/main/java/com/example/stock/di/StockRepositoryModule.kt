package com.example.stock.di

import com.example.stock.core.data.repository.StockRepository
import com.example.stock.core.data.repository.StockRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StockRepositoryModule {

    @Binds
    abstract fun bindStockRepository(
        impl: StockRepositoryImpl
    ): StockRepository
}