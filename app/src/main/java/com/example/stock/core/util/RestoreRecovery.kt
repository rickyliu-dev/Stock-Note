package com.example.stock.core.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 還原「換檔」階段的檔案操作與啟動修復，純檔案操作、可單元測試。
 *
 * 還原分成：暫存（`<db>.restoring`）→ 提交（寫 `restore.pending`）→ 換檔 → 刪除標記。
 * 不論在哪一步中斷，下次啟動時 [recover] 都會把結果收斂成「全部舊」或「全部新」：
 * - 有 `restore.pending`：已決定要換 → 把清單上仍有 `.restoring` 的檔換完，再刪標記。
 * - 沒有標記但有 `.restoring`：提交前中斷 → 刪掉殘檔，舊資料不動。
 *
 * [recover] 必須在 Room 開啟資料庫之前執行（StockApplication.onCreate 的 super.onCreate() 之前）。
 */
object RestoreRecovery {

    const val PENDING_FILE_NAME = "restore.pending"
    const val RESTORING_SUFFIX = ".restoring"
    private const val TMP_SUFFIX = ".tmp"

    /** 資料庫 dbFile 的暫存檔：同資料夾的 `<databaseName>.restoring` */
    fun stagingFileFor(dbFile: File): File = File(dbFile.parentFile, dbFile.name + RESTORING_SUFFIX)

    /**
     * 提交：把要換的資料庫檔名寫進 `restore.pending`。
     * 先寫 `.tmp`（fsync）再 rename，rename 成功的那一刻就是「決定換」的分界。
     */
    fun commit(dir: File, databaseFileNames: List<String>) {
        databaseFileNames.forEach { require(isPlainName(it)) { "不合法的資料庫檔名：$it" } }
        val tmp = File(dir, PENDING_FILE_NAME + TMP_SUFFIX)
        FileOutputStream(tmp).use { out ->
            out.write(databaseFileNames.joinToString("\n").toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        val pending = File(dir, PENDING_FILE_NAME)
        if (!tmp.renameTo(pending)) {
            tmp.delete()
            throw IOException("無法建立 $PENDING_FILE_NAME")
        }
    }

    /**
     * 啟動修復；可重複執行，修復途中再中斷也沒關係。
     *
     * @throws IOException 換檔失敗（標記會留著，下次啟動再試）
     */
    fun recover(dir: File) {
        if (!dir.isDirectory) return
        val pending = File(dir, PENDING_FILE_NAME)
        if (pending.exists()) {
            val names = pending.readText(Charsets.UTF_8).lines().map { it.trim() }.filter { it.isNotEmpty() }
            names.filter { isPlainName(it) }.forEach { name -> swap(File(dir, name)) }
            pending.delete()
        }
        // 提交前中斷留下的暫存檔、寫到一半的標記
        dir.listFiles()?.filter { it.name.endsWith(RESTORING_SUFFIX) }?.forEach { it.delete() }
        File(dir, PENDING_FILE_NAME + TMP_SUFFIX).delete()
    }

    /** 有暫存檔才換：刪除舊的 .db、-wal、-shm，再把 .restoring rename 成 .db */
    private fun swap(dbFile: File) {
        val staging = stagingFileFor(dbFile)
        if (!staging.exists()) return // 已經換過
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("無法刪除 ${file.name}")
        }
        if (!staging.renameTo(dbFile)) throw IOException("無法換上 ${dbFile.name}")
    }

    /** 只接受單純的檔名，避免標記檔內容指到資料夾外 */
    private fun isPlainName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
}
