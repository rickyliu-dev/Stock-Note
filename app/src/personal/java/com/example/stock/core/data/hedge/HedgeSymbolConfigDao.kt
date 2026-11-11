package com.example.stock.core.data.hedge

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HedgeSymbolConfigDao {

    @Query("SELECT * FROM hedge_symbol_config WHERE accountId = :accountId ORDER BY symbol")
    fun getConfigsByAccount(accountId: Long): Flow<List<HedgeSymbolConfigEntity>>

    @Upsert
    suspend fun upsert(config: HedgeSymbolConfigEntity)

    @Query("DELETE FROM hedge_symbol_config WHERE accountId = :accountId AND symbol = :symbol")
    suspend fun delete(accountId: Long, symbol: String)
}
