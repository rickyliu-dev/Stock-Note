package com.example.stock.di

import com.example.stock.navigation.FeatureEntry
import com.example.stock.navigation.SettingsEntry
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

// 只宣告集合存在、不提供任何元素：flavor 沒有 @IntoSet 時，注入的是空集合而不是編譯錯誤
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureEntryModule {

    @Multibinds
    abstract fun featureEntries(): Set<FeatureEntry>

    @Multibinds
    abstract fun settingsEntries(): Set<SettingsEntry>
}
