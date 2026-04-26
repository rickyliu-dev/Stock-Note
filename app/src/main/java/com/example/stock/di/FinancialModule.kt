package com.example.stock.di

import com.example.stock.core.data.DateProvider
import com.example.stock.core.data.DefaultDateProvider
import com.example.stock.core.data.FinancialCalculator
import com.example.stock.core.data.TaiwanFinancialCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FinancialModule {

    @Provides
    @Singleton
    fun provideFinancialCalculator(): FinancialCalculator {
        return TaiwanFinancialCalculator()
    }

    @Provides
    @Singleton
    fun provideDateProvider(): DateProvider {
        return DefaultDateProvider()
    }
}
