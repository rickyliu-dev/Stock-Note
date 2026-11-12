package com.example.stock.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** 卡 2-5 第 7 點：還原「暫存 → 提交 → 換檔 → 啟動修復」，任何時候中斷都只會是全部舊或全部新 */
class RestoreRecoveryTest {

    @get:Rule
    val tmp = TemporaryFolder()

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

    private fun localDb(name: String, version: Int): File {
        val db = sqliteFile(dbDir, name, version, "local-$name")
        File(db.path + "-wal").writeText("wal")
        File(db.path + "-shm").writeText("shm")
        return db
    }

    private fun restoringFiles() = dbDir.listFiles()!!.filter { it.name.endsWith(RestoreRecovery.RESTORING_SUFFIX) }
    private val pendingFile get() = File(dbDir, RestoreRecovery.PENDING_FILE_NAME)

    /** 兩個資料庫的本機檔與備份檔 */
    private fun twoDatabases(): Pair<List<File>, List<RestoreItem>> {
        val stockDb = localDb("stock_database", 27)
        val hedgeDb = localDb("hedge_database", 1)
        val items = listOf(
            RestoreItem("stock.db", sqliteFile(downloadDir, "stock.db", 27, "backup-stock"), stockDb, 27, true),
            RestoreItem("hedge.db", sqliteFile(downloadDir, "hedge.db", 1, "backup-hedge"), hedgeDb, 1, false)
        )
        return listOf(stockDb, hedgeDb) to items
    }

    /** 模擬 restore() 走完暫存與提交、還沒換檔就中斷 */
    private fun stageAndCommit(items: List<RestoreItem>) {
        items.forEach { it.downloaded!!.copyTo(RestoreRecovery.stagingFileFor(it.localDbFile)) }
        RestoreRecovery.commit(dbDir, items.map { it.localDbFile.name })
    }

    @Test
    fun `暫存階段失敗時本機不變且不關閉資料庫也沒有暫存殘檔`() {
        val (dbs, items) = twoDatabases()
        var closed = false
        var copies = 0

        val result = RestorePlanner.restore(
            items,
            stageCopy = { source, dest ->
                copies++
                if (copies == 2) {
                    dest.writeText("寫到一半") // 模擬空間不足：寫了一部分就失敗
                    throw IOException("No space left on device")
                }
                source.copyTo(dest)
            }
        ) { closed = true }

        assertTrue(result is RestoreResult.Failed)
        assertFalse(closed)
        assertEquals("local-stock_database", markerOf(dbs[0]))
        assertEquals("local-hedge_database", markerOf(dbs[1]))
        assertTrue(File(dbs[0].path + "-wal").exists())
        assertEquals(emptyList<File>(), restoringFiles())
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `提交後換檔到一半中斷 啟動修復後全部是新檔`() {
        val (dbs, items) = twoDatabases()
        stageAndCommit(items)
        // 換檔到一半：stock 已換好，hedge 只刪了舊 .db 還沒 rename
        dbs[0].delete(); File(dbs[0].path + "-wal").delete(); File(dbs[0].path + "-shm").delete()
        RestoreRecovery.stagingFileFor(dbs[0]).renameTo(dbs[0])
        dbs[1].delete()

        RestoreRecovery.recover(dbDir)

        assertEquals("backup-stock", markerOf(dbs[0]))
        assertEquals("backup-hedge", markerOf(dbs[1]))
        listOf(dbs[0], dbs[1]).forEach { db ->
            assertFalse(File(db.path + "-wal").exists())
            assertFalse(File(db.path + "-shm").exists())
        }
        assertEquals(emptyList<File>(), restoringFiles())
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `提交前中斷 啟動修復後全部是舊檔且殘檔被清除`() {
        val (dbs, items) = twoDatabases()
        // 暫存寫完一個、另一個寫一半，還沒寫 restore.pending
        items[0].downloaded!!.copyTo(RestoreRecovery.stagingFileFor(dbs[0]))
        RestoreRecovery.stagingFileFor(dbs[1]).writeText("寫到一半")
        File(dbDir, RestoreRecovery.PENDING_FILE_NAME + ".tmp").writeText("stock_data") // 標記寫到一半

        RestoreRecovery.recover(dbDir)

        assertEquals("local-stock_database", markerOf(dbs[0]))
        assertEquals("local-hedge_database", markerOf(dbs[1]))
        assertTrue(File(dbs[0].path + "-wal").exists())
        assertEquals(emptyList<File>(), restoringFiles())
        assertFalse(File(dbDir, RestoreRecovery.PENDING_FILE_NAME + ".tmp").exists())
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `修復重複執行結果相同`() {
        val (dbs, items) = twoDatabases()
        stageAndCommit(items)

        RestoreRecovery.recover(dbDir)
        val after1 = dbDir.listFiles()!!.map { it.name to it.readBytes().toList() }.toSet()
        RestoreRecovery.recover(dbDir)
        val after2 = dbDir.listFiles()!!.map { it.name to it.readBytes().toList() }.toSet()

        assertEquals(after1, after2)
        assertEquals("backup-stock", markerOf(dbs[0]))
        assertEquals("backup-hedge", markerOf(dbs[1]))
    }

    @Test
    fun `沒有中斷痕跡時修復不動任何檔案`() {
        val (dbs, _) = twoDatabases()
        val before = dbDir.listFiles()!!.map { it.name to it.readBytes().toList() }.toSet()

        RestoreRecovery.recover(dbDir)

        assertEquals(before, dbDir.listFiles()!!.map { it.name to it.readBytes().toList() }.toSet())
        assertEquals("local-stock_database", markerOf(dbs[0]))
    }

    @Test
    fun `標記檔內含路徑時不會動到資料夾外的檔案`() {
        val outside = File(tmp.root, "outside.db").apply { writeText("keep") }
        File(tmp.root, "outside.db.restoring").writeText("evil")
        pendingFile.writeText("../outside.db")

        RestoreRecovery.recover(dbDir)

        assertEquals("keep", outside.readText())
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `正常還原後沒有標記與暫存檔`() {
        val (_, items) = twoDatabases()

        val result = RestorePlanner.restore(items) {}

        assertEquals(RestoreResult.Success, result)
        assertEquals(emptyList<File>(), restoringFiles())
        assertFalse(pendingFile.exists())
    }
}
