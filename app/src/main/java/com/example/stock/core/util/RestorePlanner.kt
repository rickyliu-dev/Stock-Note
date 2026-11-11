package com.example.stock.core.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * 還原時一個資料庫的資料。
 *
 * @param fileName 顯示用名稱（Drive 檔名），出錯時放進訊息
 * @param downloaded 已下載到暫存資料夾的備份檔；雲端沒有此檔時為 null
 * @param localDbFile 手機上的資料庫檔（context.getDatabasePath 的結果）
 * @param currentVersion App 目前該資料庫的版本
 * @param required 缺檔時是否整個還原失敗
 */
data class RestoreItem(
    val fileName: String,
    val downloaded: File?,
    val localDbFile: File,
    val currentVersion: Int,
    val required: Boolean
)

/** 還原結果；失敗時 userMessage 可直接顯示給使用者 */
sealed interface RestoreResult {
    val userMessage: String

    data object Success : RestoreResult {
        override val userMessage = "還原成功，正在重啟..."
    }

    data class MissingRequired(val fileName: String) : RestoreResult {
        override val userMessage = "雲端找不到備份檔（$fileName）"
    }

    data class NewerVersion(val fileName: String, val backupVersion: Int, val appVersion: Int) : RestoreResult {
        override val userMessage = "備份來自較新版本的 App，請先更新 App"
    }

    data class InvalidFile(val fileName: String) : RestoreResult {
        override val userMessage = "備份檔損毀或格式不正確（$fileName）"
    }

    data class Failed(val reason: String) : RestoreResult {
        override val userMessage = "還原失敗：$reason"
    }
}

/**
 * 還原的「檢查與覆蓋」，不依賴 Android 與 Drive，方便單元測試。
 *
 * 流程：全部檢查通過 → 呼叫 beforeOverwrite（關閉資料庫）→ 逐一覆蓋。
 * 任何一項檢查失敗，本機檔案完全不動。
 */
object RestorePlanner {

    private const val SQLITE_HEADER_SIZE = 100
    private const val USER_VERSION_OFFSET = 60L
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun restore(items: Collection<RestoreItem>, beforeOverwrite: () -> Unit): RestoreResult {
        check(items)?.let { return it }

        beforeOverwrite()
        items.filter { it.downloaded != null }.forEach { overwrite(it.downloaded!!, it.localDbFile) }
        return RestoreResult.Success
    }

    /** 回傳第一個失敗原因；全部通過回傳 null */
    fun check(items: Collection<RestoreItem>): RestoreResult? {
        for (item in items) {
            val downloaded = item.downloaded
            if (downloaded == null || !downloaded.exists()) {
                // 非 required 缺檔（例如舊備份沒有 hedge）→ 該資料庫本機不動
                if (item.required) return RestoreResult.MissingRequired(item.fileName)
                continue
            }
            val backupVersion = readUserVersion(downloaded) ?: return RestoreResult.InvalidFile(item.fileName)
            if (backupVersion > item.currentVersion) {
                return RestoreResult.NewerVersion(item.fileName, backupVersion, item.currentVersion)
            }
        }
        return null
    }

    /** 讀 SQLite 檔頭的 user_version（offset 60，4 bytes big-endian）；不是 SQLite 檔回傳 null */
    fun readUserVersion(file: File): Int? {
        if (file.length() < SQLITE_HEADER_SIZE) return null
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(SQLITE_MAGIC.size)
            raf.readFully(magic)
            if (!magic.contentEquals(SQLITE_MAGIC)) return null
            raf.seek(USER_VERSION_OFFSET)
            return raf.readInt()
        }
    }

    /** 沿用原本做法：刪除 .db、-wal、-shm，再複製備份檔 */
    private fun overwrite(source: File, dbFile: File) {
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { file ->
            if (file.exists()) file.delete()
        }
        FileInputStream(source).use { input ->
            FileOutputStream(dbFile).use { output ->
                input.channel.transferTo(0, input.channel.size(), output.channel)
                output.flush()
                output.channel.force(true)
            }
        }
    }
}
