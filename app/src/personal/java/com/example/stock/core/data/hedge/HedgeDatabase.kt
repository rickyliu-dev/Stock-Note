package com.example.stock.core.data.hedge

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 避險資料庫（只有 personal 有），手機上的檔名 "hedge_database"。
 *
 * 版本號與核心 StockDatabase 各自獨立，從 1 開始。
 * 遷移規則與核心相同：不使用 destructive migration，版本紀錄見 docs/database-migrations.md。
 */
@Database(
    entities = [HedgeSymbolConfigEntity::class],
    version = 1,
    exportSchema = true
)
abstract class HedgeDatabase : RoomDatabase() {
    abstract fun hedgeSymbolConfigDao(): HedgeSymbolConfigDao

    companion object {
        const val DATABASE_NAME = "hedge_database"
    }
}
