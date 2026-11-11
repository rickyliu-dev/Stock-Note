package com.example.stock.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.stock.BuildConfig
import com.example.stock.core.data.hedge.HedgeDatabase
import com.example.stock.core.data.hedge.HedgeSymbolConfigDao
import com.example.stock.core.data.hedge.HedgeSymbolConfigRepositoryImpl
import com.example.stock.core.domain.hedge.HedgeSymbolConfigRepository
import com.example.stock.core.util.BackupTarget
import com.example.stock.core.util.driveFileNameFor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * 避險資料庫（只有 personal 有）：資料庫、DAO、Repository，
 * 以及加進核心 Set<BackupTarget>（見 src/main 的 BackupModule）的備份目標。
 */
@Module
@InstallIn(SingletonComponent::class)
object HedgeDatabaseModule {

    // 不使用 fallbackToDestructiveMigration()：遷移規則見 docs/database-migrations.md
    @Provides
    @Singleton
    fun provideHedgeDatabase(@ApplicationContext context: Context): HedgeDatabase =
        Room.databaseBuilder(
            context,
            HedgeDatabase::class.java,
            HedgeDatabase.DATABASE_NAME
        ).build()

    @Provides
    fun provideHedgeSymbolConfigDao(database: HedgeDatabase): HedgeSymbolConfigDao =
        database.hedgeSymbolConfigDao()

    @Provides
    fun provideHedgeSymbolConfigRepository(
        impl: HedgeSymbolConfigRepositoryImpl
    ): HedgeSymbolConfigRepository = impl

    /** 舊備份可能沒有 hedge 檔，所以還原時缺少不算失敗 */
    @Provides
    @IntoSet
    fun provideHedgeBackupTarget(hedgeDatabase: HedgeDatabase): BackupTarget = object : BackupTarget {
        override val databaseName = HedgeDatabase.DATABASE_NAME
        override val driveFileName = driveFileNameFor("hedge_ledger", BuildConfig.APPLICATION_ID)
        override val database: RoomDatabase = hedgeDatabase
        override val required = false
    }
}
