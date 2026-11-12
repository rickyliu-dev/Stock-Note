package com.example.stock

import android.app.Application
import android.util.Log
import com.example.stock.core.util.RestoreRecovery
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

    override fun onCreate() {
        // 必須在 super.onCreate()（Hilt 注入、建立資料庫）之前：收尾上次中斷的還原
        try {
            getDatabasePath(RESTORE_PROBE_DATABASE).parentFile?.let { RestoreRecovery.recover(it) }
        } catch (e: Exception) {
            Log.e("StockApplication", "還原修復失敗，下次啟動再試", e)
        }
        super.onCreate()
    }

    private companion object {
        /** 只用來取得 databases 資料夾，所有資料庫都在同一個資料夾 */
        const val RESTORE_PROBE_DATABASE = "stock_database"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java).workerFactory()
            )
            .build()
}
