package com.example.stock.core.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.stock.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地備份：把所有 BackupTarget 打包成 zip，存到使用者用系統檔案選擇器選的位置；不需要登入 Google。
 * 還原沿用 RestorePlanner 的「檢查與覆蓋」，與雲端還原相同。
 */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupTargets: Set<@JvmSuppressWildcards BackupTarget>
) {
    private companion object {
        const val TAG = "LocalBackupManager"
        const val BACKUP_TEMP_DIR = "local_backup_tmp"
        const val RESTORE_TEMP_DIR = "local_restore_tmp"
    }

    /** 備份到 uri（CreateDocument 取得）；成功回傳 true */
    suspend fun backupTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, BACKUP_TEMP_DIR)
        try {
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            val files = linkedMapOf<String, File>()
            val databases = backupTargets.sortedBy { it.databaseName }.map { target ->
                val entryName = BackupZip.dbEntryName(target.databaseName)
                val snapshot = File(tempDir, entryName)
                DatabaseSnapshot.checkpoint(target.database)
                DatabaseSnapshot.copy(context.getDatabasePath(target.databaseName), snapshot)
                files[entryName] = snapshot
                BackupManifestDatabase(
                    databaseName = target.databaseName,
                    fileName = entryName,
                    userVersion = RestorePlanner.readUserVersion(snapshot)
                        ?: target.database.openHelper.readableDatabase.version
                )
            }
            val manifest = BackupManifest(
                formatVersion = BackupManifest.CURRENT_FORMAT_VERSION,
                appVersionName = BuildConfig.VERSION_NAME,
                createdAt = Instant.now().toString(),
                databases = databases
            )

            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("無法開啟目的檔案")
            output.use { BackupZip.write(it, manifest, files) }
            Log.d(TAG, "本地備份完成：${databases.joinToString { "${it.fileName}(v${it.userVersion})" }}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "本地備份失敗", e)
            false
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * 從 uri（OpenDocument 取得）還原：解壓到暫存資料夾 → 讀 manifest → RestorePlanner 檢查與覆蓋。
     * 成功時會重啟 App 進程。
     */
    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, RESTORE_TEMP_DIR)
        try {
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext RestoreResult.Failed("無法開啟備份檔")
            input.use { BackupZip.extract(it, tempDir) }

            val manifest = BackupZip.readManifest(tempDir)
                ?: return@withContext RestoreResult.InvalidFile(BackupManifest.FILE_NAME)
            Log.d(TAG, "備份檔來自 App ${manifest.appVersionName}，建立於 ${manifest.createdAt}")

            val items = backupTargets.map { target ->
                BackupZip.restoreItem(
                    extractedDir = tempDir,
                    databaseName = target.databaseName,
                    localDbFile = context.getDatabasePath(target.databaseName),
                    currentVersion = target.database.openHelper.readableDatabase.version,
                    required = target.required
                )
            }

            val result = RestorePlanner.restore(items) {
                backupTargets.forEach { it.database.close() }
            }
            if (result !is RestoreResult.Success) {
                Log.e(TAG, "本地還原中止: $result")
                return@withContext result
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
                delay(500)
                AppRestarter.restart(context)
            }
            result
        } catch (e: UnsafeZipEntryException) {
            Log.e(TAG, "備份檔含不安全的路徑", e)
            RestoreResult.InvalidFile(e.entryName)
        } catch (e: java.util.zip.ZipException) {
            Log.e(TAG, "不是有效的 zip", e)
            RestoreResult.InvalidFile("zip")
        } catch (e: Exception) {
            Log.e(TAG, "本地還原異常", e)
            RestoreResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
