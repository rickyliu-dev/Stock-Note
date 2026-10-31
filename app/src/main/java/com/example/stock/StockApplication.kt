package com.example.stock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class StockApplication : Application(), Configuration.Provider {

    // 透過 EntryPoint 取得 HiltWorkerFactory，讓 WorkManager 建立的 Worker 也能使用 Hilt 注入
    // (不用 @Inject 欄位：目前的 Hilt 2.51.1 讀不到 Kotlin 2.1 編譯出的欄位資訊，會編譯失敗)
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java).workerFactory()
            )
            .build()
}
