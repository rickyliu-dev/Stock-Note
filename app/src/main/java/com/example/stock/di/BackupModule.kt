package com.example.stock.di

import androidx.room.RoomDatabase
import com.example.stock.BuildConfig
import com.example.stock.core.util.BackupTarget
import com.example.stock.core.util.driveFileNameFor
import com.example.stock.dataBase.StockDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    // 宣告集合存在；各 flavor 可再用 @IntoSet 加入自己的資料庫
    @Multibinds
    abstract fun backupTargets(): Set<BackupTarget>

    companion object {
        /** 核心資料庫：所有變體都有，還原時必須存在 */
        @Provides
        @IntoSet
        fun provideStockBackupTarget(stockDatabase: StockDatabase): BackupTarget = object : BackupTarget {
            override val databaseName = "stock_database"
            override val driveFileName = driveFileNameFor("stock_ledger", BuildConfig.APPLICATION_ID)
            override val database: RoomDatabase = stockDatabase
            override val required = true
        }
    }
}
