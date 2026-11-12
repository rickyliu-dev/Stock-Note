package com.example.stock.di

import com.example.stock.feature.setting.CryptoSettingsEntry
import com.example.stock.feature.setting.UsStockSettingsEntry
import com.example.stock.navigation.SettingsEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

// 把美股、虛擬貨幣設定加進核心宣告的 Set<SettingsEntry>（見 src/main 的 FeatureEntryModule）
@Module
@InstallIn(SingletonComponent::class)
abstract class MarketSettingsModule {

    @Binds
    @IntoSet
    abstract fun bindUsStockSettingsEntry(entry: UsStockSettingsEntry): SettingsEntry

    @Binds
    @IntoSet
    abstract fun bindCryptoSettingsEntry(entry: CryptoSettingsEntry): SettingsEntry
}
