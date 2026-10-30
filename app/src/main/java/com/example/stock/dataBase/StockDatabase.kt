package com.example.stock.dataBase

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.model.Converters
import com.example.stock.core.data.model.StockPriceEntity
import com.example.stock.core.data.model.Transaction
import com.example.stock.core.data.model.TransactionDao

@Database(
    entities = [Transaction::class, StockPriceEntity::class, Account::class],
    version = 26,
    autoMigrations = [
        AutoMigration(from = 25, to = 26) // 新增 transactions (accountId, symbol, date) 索引
    ]
)
@TypeConverters(Converters::class)
abstract class StockDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}