package com.example.stock.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipTest {

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

    private val manifest = BackupManifest(
        formatVersion = 1,
        appVersionName = "0.1.3",
        createdAt = "2026-11-11T08:30:00Z",
        databases = listOf(
            BackupManifestDatabase("stock_database", "stock_database.db", 27),
            BackupManifestDatabase("hedge_database", "hedge_database.db", 1)
        )
    )

    /** 直接用 ZipOutputStream 做出任意內容的 zip（模擬惡意或損毀的備份檔） */
    private fun rawZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    // ---------- manifest ----------

    @Test
    fun `manifest 寫成 JSON 再讀回內容相同`() {
        assertEquals(manifest, BackupManifest.fromJson(manifest.toJson()))
    }

    @Test
    fun `manifest 有未知欄位時仍可讀取`() {
        val json = """
            {"formatVersion":1,"appVersionName":"9.9.9","createdAt":"2030-01-01T00:00:00Z","futureField":true,
             "databases":[{"databaseName":"stock_database","fileName":"stock_database.db","userVersion":30,"extra":1}]}
        """.trimIndent()

        val parsed = BackupManifest.fromJson(json)

        assertEquals("9.9.9", parsed?.appVersionName)
        assertEquals(30, parsed?.databases?.single()?.userVersion)
    }

    @Test
    fun `manifest 格式錯誤時回傳 null`() {
        assertNull(BackupManifest.fromJson("not json"))
        assertNull(BackupManifest.fromJson("""{"formatVersion":1}"""))
    }

    @Test
    fun `zip 寫出後解壓可讀回 manifest 與資料庫`() {
        val src = tmp.newFolder("src")
        val stock = sqliteFile(src, "a.db", 27, "stock")
        val bytes = ByteArrayOutputStream()
        BackupZip.write(bytes, manifest, mapOf("stock_database.db" to stock))

        val out = tmp.newFolder("out")
        BackupZip.extract(ByteArrayInputStream(bytes.toByteArray()), out)

        assertEquals(manifest, BackupZip.readManifest(out))
        assertEquals("stock", markerOf(File(out, "stock_database.db")))
    }

    @Test
    fun `沒有 manifest 或格式版本比 App 新時讀不到 manifest`() {
        val noManifest = tmp.newFolder("none")
        assertNull(BackupZip.readManifest(noManifest))

        val newer = tmp.newFolder("newer")
        File(newer, BackupManifest.FILE_NAME).writeText(
            manifest.copy(formatVersion = BackupManifest.CURRENT_FORMAT_VERSION + 1).toJson()
        )
        assertNull(BackupZip.readManifest(newer))
    }

    // ---------- 路徑穿越 ----------

    @Test
    fun `zip 內含上層路徑時拒絕解壓且不會寫到資料夾外`() {
        val root = tmp.newFolder("root")
        val dest = File(root, "dest")
        val zip = rawZip("manifest.json" to "{}".toByteArray(), "../evil.db" to "x".toByteArray())

        try {
            BackupZip.extract(ByteArrayInputStream(zip), dest)
            fail("應該拒絕")
        } catch (e: UnsafeZipEntryException) {
            assertEquals("../evil.db", e.entryName)
        }
        assertFalse(File(root, "evil.db").exists())
    }

    @Test
    fun `zip 內含絕對路徑或子資料夾時拒絕解壓`() {
        listOf("/data/data/x.db", "C:\\x.db", "sub/x.db", "..\\x.db", "..").forEach { name ->
            val dest = tmp.newFolder()
            try {
                BackupZip.extract(ByteArrayInputStream(rawZip(name to "x".toByteArray())), dest)
                fail("應該拒絕：$name")
            } catch (e: UnsafeZipEntryException) {
                assertEquals(name, e.entryName)
            }
        }
    }

    @Test
    fun `安全檔名判斷`() {
        assertTrue(BackupZip.isSafeEntryName("stock_database.db"))
        assertTrue(BackupZip.isSafeEntryName("manifest.json"))
        assertFalse(BackupZip.isSafeEntryName(""))
        assertFalse(BackupZip.isSafeEntryName("../a.db"))
        assertFalse(BackupZip.isSafeEntryName("/a.db"))
        assertFalse(BackupZip.isSafeEntryName("a/b.db"))
        assertFalse(BackupZip.isSafeEntryName("C:a.db"))
    }

    // ---------- 還原項目 ----------

    @Test
    fun `zip 缺少 required 資料庫時還原失敗且本機不變`() {
        // personal 只留 hedge 的備份（核心檔缺少）
        val src = tmp.newFolder("src2")
        val bytes = ByteArrayOutputStream()
        BackupZip.write(bytes, manifest, mapOf("hedge_database.db" to sqliteFile(src, "h.db", 1, "backup-hedge")))
        val extracted = tmp.newFolder("extracted")
        BackupZip.extract(ByteArrayInputStream(bytes.toByteArray()), extracted)

        val dbDir = tmp.newFolder("databases")
        val stockDb = sqliteFile(dbDir, "stock_database", 27, "local-stock")
        val hedgeDb = sqliteFile(dbDir, "hedge_database", 1, "local-hedge")
        var closed = false

        val result = RestorePlanner.restore(
            listOf(
                BackupZip.restoreItem(extracted, "stock_database", stockDb, 27, required = true),
                BackupZip.restoreItem(extracted, "hedge_database", hedgeDb, 1, required = false)
            )
        ) { closed = true }

        assertEquals(RestoreResult.MissingRequired("stock_database.db"), result)
        assertEquals("備份檔中找不到必要的檔案（stock_database.db）", result.userMessage)
        assertFalse(closed)
        assertEquals("local-stock", markerOf(stockDb))
        assertEquals("local-hedge", markerOf(hedgeDb))
    }

    @Test
    fun `缺檔訊息依來源區分雲端與本地檔案`() {
        assertEquals(
            "雲端找不到這個版本的備份檔（stock_ledger.debug.db）",
            RestoreResult.MissingRequired("stock_ledger.debug.db", BackupSource.CLOUD).userMessage
        )
        assertEquals(
            "備份檔中找不到必要的檔案（stock_database.db）",
            RestoreResult.MissingRequired("stock_database.db", BackupSource.FILE).userMessage
        )
    }

    @Test
    fun `basic 還原 personal 的備份時 hedge 檔被忽略`() {
        val src = tmp.newFolder("src3")
        val bytes = ByteArrayOutputStream()
        BackupZip.write(
            bytes, manifest,
            mapOf(
                "stock_database.db" to sqliteFile(src, "s.db", 27, "backup-stock"),
                "hedge_database.db" to sqliteFile(src, "h.db", 1, "backup-hedge")
            )
        )
        val extracted = tmp.newFolder("extracted3")
        BackupZip.extract(ByteArrayInputStream(bytes.toByteArray()), extracted)
        val dbDir = tmp.newFolder("databases3")
        val stockDb = sqliteFile(dbDir, "stock_database", 27, "local-stock")

        // basic 只有核心這一個 BackupTarget
        val item = BackupZip.restoreItem(extracted, "stock_database", stockDb, 27, required = true)
        assertNotNull(item.downloaded)
        val result = RestorePlanner.restore(listOf(item)) {}

        assertEquals(RestoreResult.Success, result)
        assertEquals("backup-stock", markerOf(stockDb))
        assertFalse(File(dbDir, "hedge_database").exists())
    }
}
