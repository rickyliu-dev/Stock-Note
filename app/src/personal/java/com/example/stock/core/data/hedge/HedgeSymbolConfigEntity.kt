package com.example.stock.core.data.hedge

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * hedge_symbol_config 表：避險標的設定，主鍵 (accountId, symbol)。
 *
 * accountId 對應核心 StockDatabase 的 accounts.id，但兩個資料庫之間無法建外鍵，
 * 帳戶刪除時這裡的資料不會自動清掉。
 */
@Entity(
    tableName = "hedge_symbol_config",
    primaryKeys = ["accountId", "symbol"]
)
data class HedgeSymbolConfigEntity(
    val accountId: Long,
    val symbol: String,
    @ColumnInfo(defaultValue = "0")
    val includeInExposure: Boolean = false,
    /** 市值加權 ×100（1 倍 = 100，00685L 正2 = 200） */
    @ColumnInfo(defaultValue = "100")
    val weightTimes100: Long = 100L
)
