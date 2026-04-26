package com.example.stock.dataBase

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
    version = 3
)
@TypeConverters(Converters::class)
abstract class StockDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}