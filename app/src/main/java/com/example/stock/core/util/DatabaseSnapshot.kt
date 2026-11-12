package com.example.stock.core.util

import android.util.Log
import androidx.room.RoomDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 備份前取得資料庫檔的一致快照：先 checkpoint 把 WAL 寫回 .db，再複製 .db。
 * 雲端備份（GoogleDriveManager）與本地備份（LocalBackupManager）共用。
 */
object DatabaseSnapshot {

    private const val TAG = "DatabaseSnapshot"

    /** PRAGMA wal_checkpoint(TRUNCATE)；失敗只記 log，沿用原本雲端備份的行為 */
    fun checkpoint(database: RoomDatabase) {
        try {
            val sdb = database.openHelper.writableDatabase
            val cursor = sdb.query("PRAGMA wal_checkpoint(TRUNCATE)")
            if (cursor.moveToFirst()) {
                val busy = cursor.getInt(0)
                val log = cursor.getInt(1)
                val checkpointed = cursor.getInt(2)
                Log.d(TAG, "[Checkpoint 詳情] Busy: $busy, Log Frames: $log, Checkpointed: $checkpointed")
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Checkpoint 執行異常: ${e.message}")
        }
    }

    /** 複製資料庫檔並 fsync */
    fun copy(dbFile: File, dest: File) {
        FileInputStream(dbFile).use { input ->
            FileOutputStream(dest).use { output ->
                input.channel.transferTo(0, input.channel.size(), output.channel)
                output.flush()
                output.channel.force(true)
            }
        }
    }
}
