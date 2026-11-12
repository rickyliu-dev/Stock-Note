package com.example.stock.di

import com.example.stock.core.data.model.MarketAvailability
import com.example.stock.core.data.model.PersonalMarketAvailability
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketAvailabilityModule {

    @Binds
    abstract fun bindMarketAvailability(impl: PersonalMarketAvailability): MarketAvailability
}
