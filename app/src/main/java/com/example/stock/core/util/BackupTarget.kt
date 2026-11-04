package com.example.stock.core.util

import androidx.room.RoomDatabase

/**
 * 要備份／還原的一個資料庫（一個 @Database 類別 ＝ 手機上一個 .db 檔）。
 *
 * 核心提供 StockDatabase 的 target；各 flavor 可用 @IntoSet 再加入自己的資料庫（例如 personal 的 hedge）。
 * GoogleDriveManager 會逐一處理注入進來的 Set<BackupTarget>。
 */
interface BackupTarget {
    /** 手機上的資料庫檔名，例如 "stock_database"（context.getDatabasePath 用） */
    val databaseName: String

    /** Drive appDataFolder 中的檔名，用 driveFileNameFor(..., BuildConfig.APPLICATION_ID) 計算 */
    val driveFileName: String

    /** 用來做 PRAGMA wal_checkpoint、讀取目前版本與還原前 close */
    val database: RoomDatabase

    /** 還原時雲端缺少此檔是否視為整個還原失敗 */
    val required: Boolean
}
