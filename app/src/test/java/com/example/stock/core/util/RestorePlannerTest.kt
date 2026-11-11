package com.example.stock.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer

class RestorePlannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 產生一個最小的 SQLite 檔：100 bytes 檔頭（magic ＋ user_version）＋ 用來辨識內容的標記 */
    private fun sqliteFile(dir: File, name: String, userVersion: Int, marker: String): File {
        val header = ByteBuffer.allocate(100)
        header.put("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        header.putInt(60, userVersion)
        return File(dir, name).apply {
            parentFile?.mkdirs()
            writeBytes(header.array() + marker.toByteArray())
        }
    }

    private fun markerOf(file: File): String = file.readBytes().drop(100).toByteArray().decodeToString()

    private val downloadDir by lazy { tmp.newFolder("download") }
    private val dbDir by lazy { tmp.newFolder("databases") }

    /** 本機已有的資料庫檔，連同 -wal、-shm */
    private fun localDb(name: String, version: Int): File {
        val db = sqliteFile(dbDir, name, version, "local-$name")
        File(db.path + "-wal").writeText("wal")
        File(db.path + "-shm").writeText("shm")
        return db
    }

    @Test
    fun `required 缺檔時整個還原失敗且本機檔案不變`() {
        val stockDb = localDb("stock_database", 27)
        val hedgeDb = localDb("hedge_database", 1)
        val hedgeBackup = sqliteFile(downloadDir, "hedge_ledger.db", 1, "backup-hedge")
        var closed = false

        val result = RestorePlanner.restore(
            listOf(
                RestoreItem("stock_ledger.db", null, stockDb, 27, required = true),
                RestoreItem("hedge_ledger.db", hedgeBackup, hedgeDb, 1, required = false)
            )
        ) { closed = true }

        assertEquals(RestoreResult.MissingRequired("stock_ledger.db"), result)
        assertFalse(closed)
        assertEquals("local-stock_database", markerOf(stockDb))
        assertEquals("local-hedge_database", markerOf(hedgeDb))
        assertTrue(File(stockDb.path + "-wal").exists())
        assertTrue(File(hedgeDb.path + "-wal").exists())
    }

    @Test
    fun `非 required 缺檔時該資料庫不動其餘照常還原`() {
        val stockDb = localDb("stock_database", 27)
        val hedgeDb = localDb("hedge_database", 1)
        val stockBackup = sqliteFile(downloadDir, "stock_ledger.db", 27, "backup-stock")

        val result = RestorePlanner.restore(
            listOf(
                RestoreItem("stock_ledger.db", stockBackup, stockDb, 27, required = true),
                RestoreItem("hedge_ledger.db", null, hedgeDb, 1, required = false)
            )
        ) {}

        assertEquals(RestoreResult.Success, result)
        assertEquals("backup-stock", markerOf(stockDb))
        assertFalse(File(stockDb.path + "-wal").exists())
        assertFalse(File(stockDb.path + "-shm").exists())
        assertEquals("local-hedge_database", markerOf(hedgeDb))
        assertTrue(File(hedgeDb.path + "-wal").exists())
    }

    @Test
    fun `備份的 user_version 較新時拒絕還原且本機檔案不變`() {
        val stockDb = localDb("stock_database", 27)
        val hedgeDb = localDb("hedge_database", 1)
        val stockBackup = sqliteFile(downloadDir, "stock_ledger.db", 27, "backup-stock")
        val hedgeBackup = sqliteFile(downloadDir, "hedge_ledger.db", 2, "backup-hedge")
        var closed = false

        // 較新的是第二個檔：第一個也不能先被覆蓋
        val result = RestorePlanner.restore(
            listOf(
                RestoreItem("stock_ledger.db", stockBackup, stockDb, 27, required = true),
                RestoreItem("hedge_ledger.db", hedgeBackup, hedgeDb, 1, required = false)
            )
        ) { closed = true }

        assertEquals(RestoreResult.NewerVersion("hedge_ledger.db", 2, 1), result)
        assertEquals("備份來自較新版本的 App，請先更新 App", result.userMessage)
        assertFalse(closed)
        assertEquals("local-stock_database", markerOf(stockDb))
        assertEquals("local-hedge_database", markerOf(hedgeDb))
    }

    @Test
    fun `正常情況全部覆蓋並刪除 wal 與 shm`() {
        val stockDb = localDb("stock_database", 27)
        val hedgeDb = localDb("hedge_database", 1)
        val stockBackup = sqliteFile(downloadDir, "stock_ledger.db", 25, "backup-stock") // 較舊版本，交給 Room 遷移
        val hedgeBackup = sqliteFile(downloadDir, "hedge_ledger.db", 1, "backup-hedge")
        var closed = false

        val result = RestorePlanner.restore(
            listOf(
                RestoreItem("stock_ledger.db", stockBackup, stockDb, 27, required = true),
                RestoreItem("hedge_ledger.db", hedgeBackup, hedgeDb, 1, required = false)
            )
        ) { closed = true }

        assertEquals(RestoreResult.Success, result)
        assertTrue(closed)
        assertEquals("backup-stock", markerOf(stockDb))
        assertEquals("backup-hedge", markerOf(hedgeDb))
        listOf(stockDb, hedgeDb).forEach { db ->
            assertFalse(File(db.path + "-wal").exists())
            assertFalse(File(db.path + "-shm").exists())
        }
    }

    @Test
    fun `不是 SQLite 檔時拒絕還原`() {
        val stockDb = localDb("stock_database", 27)
        val broken = File(downloadDir, "stock_ledger.db").apply { writeText("<html>not a database</html>") }

        val result = RestorePlanner.restore(
            listOf(RestoreItem("stock_ledger.db", broken, stockDb, 27, required = true))
        ) {}

        assertEquals(RestoreResult.InvalidFile("stock_ledger.db"), result)
        assertEquals("local-stock_database", markerOf(stockDb))
    }

    @Test
    fun `讀取檔頭的 user_version`() {
        assertEquals(27, RestorePlanner.readUserVersion(sqliteFile(downloadDir, "a.db", 27, "")))
        assertNull(RestorePlanner.readUserVersion(File(downloadDir, "short.db").apply { writeText("x") }))
    }
}
