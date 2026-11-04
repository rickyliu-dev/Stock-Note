package com.example.stock.di

import com.example.stock.feature.hedge.HedgeFeatureEntry
import com.example.stock.navigation.FeatureEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

// 把避險入口加進核心宣告的 Set<FeatureEntry>（見 src/main 的 FeatureEntryModule）
@Module
@InstallIn(SingletonComponent::class)
abstract class HedgeModule {

    @Binds
    @IntoSet
    abstract fun bindHedgeFeatureEntry(
        entry: HedgeFeatureEntry
    ): FeatureEntry
}
