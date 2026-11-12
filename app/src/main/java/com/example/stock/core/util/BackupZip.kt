package com.example.stock.core.util

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地備份 zip 的讀寫，不依賴 Android，方便單元測試。
 *
 * zip 內容是平的：manifest.json ＋ 每個資料庫一個 `<databaseName>.db`。
 * 用 databaseName（不是 Drive 檔名）命名，所以任何變體產生的備份都能互相還原核心資料；
 * 不屬於本 App 任何 BackupTarget 的 .db（例如 basic 還原 personal 備份時的 hedge）會被忽略。
 */
object BackupZip {

    /** 備份檔內資料庫的檔名 */
    fun dbEntryName(databaseName: String): String = "$databaseName.db"

    /** 寫出 zip：manifest.json ＋ files（zip 內檔名 → 本機檔案） */
    fun write(output: OutputStream, manifest: BackupManifest, files: Map<String, File>) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupManifest.FILE_NAME))
            zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (name, file) ->
                require(isSafeEntryName(name)) { "不合法的檔名：$name" }
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * 解壓到 destDir。只接受單層檔名；含 ../、子資料夾或絕對路徑的項目一律拒絕。
     *
     * @throws UnsafeZipEntryException 有不安全的檔名（destDir 中可能已解出部分檔案，由呼叫端刪除整個資料夾）
     */
    fun extract(input: InputStream, destDir: File) {
        destDir.mkdirs()
        val destPath = destDir.canonicalFile
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (entry.isDirectory || !isSafeEntryName(name)) throw UnsafeZipEntryException(name)
                val out = File(destPath, name)
                // 再用 canonical path 確認一次真的在 destDir 裡
                if (out.canonicalFile.parentFile != destPath) throw UnsafeZipEntryException(name)
                FileOutputStream(out).use { zip.copyTo(it) }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /**
     * 為一個資料庫建立 RestoreItem：解壓資料夾中有 `<databaseName>.db` 就用它，沒有則 downloaded = null。
     */
    fun restoreItem(
        extractedDir: File,
        databaseName: String,
        localDbFile: File,
        currentVersion: Int,
        required: Boolean
    ): RestoreItem {
        val name = dbEntryName(databaseName)
        return RestoreItem(
            fileName = name,
            downloaded = File(extractedDir, name).takeIf { it.isFile },
            localDbFile = localDbFile,
            currentVersion = currentVersion,
            required = required
        )
    }

    /** 讀 manifest.json；不存在、格式錯誤或格式版本比 App 新都回傳 null */
    fun readManifest(extractedDir: File): BackupManifest? {
        val file = File(extractedDir, BackupManifest.FILE_NAME)
        if (!file.isFile) return null
        val manifest = BackupManifest.fromJson(file.readText(Charsets.UTF_8)) ?: return null
        return manifest.takeIf { it.formatVersion in 1..BackupManifest.CURRENT_FORMAT_VERSION }
    }

    /** 單層檔名：不可為空、不可是 . 或 ..、不可含 / 或 \、不可是磁碟路徑（C:） */
    fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." && name != ".." &&
            !name.contains('/') && !name.contains('\\') &&
            !name.contains(':') &&
            name.none { it.code < 0x20 }
}
