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

    /** source 由呼叫端標明（RestorePlanner 不知道來源，預設 FILE；雲端還原改成 CLOUD） */
    data class MissingRequired(val fileName: String, val source: BackupSource = BackupSource.FILE) : RestoreResult {
        override val userMessage = when (source) {
            BackupSource.CLOUD -> "雲端找不到這個版本的備份檔（$fileName）"
            BackupSource.FILE -> "備份檔中找不到必要的檔案（$fileName）"
        }
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
 * 流程：全部檢查通過 → 暫存 → 提交 → 呼叫 beforeOverwrite（關閉資料庫）→ 換檔（見 [RestoreRecovery]）。
 * 任何一項檢查或暫存失敗，本機檔案完全不動。
 */
object RestorePlanner {

    private const val SQLITE_HEADER_SIZE = 100
    private const val USER_VERSION_OFFSET = 60L
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * 全部檢查通過後：暫存 → 提交 → 呼叫 beforeOverwrite（關閉資料庫）→ 換檔 → 刪除標記。
     * 結果只會是「全部舊」或「全部新」；中斷時由 [RestoreRecovery.recover] 在下次啟動收尾。
     * 所有資料庫檔必須在同一個資料夾（context.getDatabasePath 的結果都是）。
     */
    fun restore(items: Collection<RestoreItem>, beforeOverwrite: () -> Unit): RestoreResult =
        restore(items, ::copyWithSync, beforeOverwrite)

    /** stageCopy 可替換，讓單元測試模擬暫存階段寫檔失敗 */
    internal fun restore(
        items: Collection<RestoreItem>,
        stageCopy: (source: File, dest: File) -> Unit,
        beforeOverwrite: () -> Unit
    ): RestoreResult {
        check(items)?.let { return it }

        val toRestore = items.filter { it.downloaded != null }
        val dir = (toRestore.firstOrNull() ?: items.firstOrNull())?.localDbFile?.parentFile
            ?: return RestoreResult.Failed("沒有要還原的資料庫")
        require(toRestore.all { it.localDbFile.parentFile == dir }) { "所有資料庫檔必須在同一個資料夾" }

        // 1. 暫存：失敗就刪掉暫存檔，資料庫不關、本機完全不動
        val staged = mutableListOf<File>()
        try {
            toRestore.forEach { item ->
                val staging = RestoreRecovery.stagingFileFor(item.localDbFile)
                staged += staging
                stageCopy(item.downloaded!!, staging)
            }
            // 2. 提交：rename 成功之後就一定要換完
            RestoreRecovery.commit(dir, toRestore.map { it.localDbFile.name })
        } catch (e: Exception) {
            staged.forEach { it.delete() }
            return RestoreResult.Failed("寫入暫存檔失敗（${e.message ?: e.javaClass.simpleName}），本機資料未變更")
        }

        // 3. 換檔 4. 刪除標記
        beforeOverwrite()
        return try {
            RestoreRecovery.recover(dir)
            RestoreResult.Success
        } catch (e: Exception) {
            RestoreResult.Failed("換檔未完成，請重新開啟 App，會自動完成還原")
        }
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

    /** 複製並 fsync，確保暫存檔真的寫進儲存裝置 */
    private fun copyWithSync(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.channel.transferTo(0, input.channel.size(), output.channel)
                output.flush()
                output.channel.force(true)
            }
        }
    }
}
