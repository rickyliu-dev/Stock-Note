package com.example.stock.core.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupTargets: Set<@JvmSuppressWildcards BackupTarget>
) {
    companion object {
        private const val TAG = "GoogleDriveManager"
        private const val RESTORE_TEMP_DIR = "restore_tmp"
    }

    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * 登出 Google 帳號，清除登入狀態以便使用者更換帳號
     */
    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut()
            // 同時清除快取中的認證資訊
            android.util.Log.d(TAG, "Google 帳號已成功登出")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Google 登出失敗", e)
            false
        }
    }

    fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Stock App").build()
    }

    /**
     * 逐一備份所有 BackupTarget，任何一個失敗就回傳 false（其餘仍會嘗試備份）。
     * 每個資料庫採搜尋並覆蓋 (Search-and-Update) 模式，
     * 確保 appDataFolder 中每個 driveFileName 永遠只有一個檔案 ID。
     */
    suspend fun backupDatabase(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        val driveService = try {
            getDriveService(account)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "建立 Drive 服務失敗", e)
            return@withContext false
        }
        var allSucceeded = true
        backupTargets.forEach { target ->
            if (!backupOne(driveService, target)) allSucceeded = false
        }
        allSucceeded
    }

    private fun backupOne(driveService: Drive, target: BackupTarget): Boolean {
        val dbFile = context.getDatabasePath(target.databaseName)
        val backupTempFile = File(context.cacheDir, "backup_ready_${target.databaseName}.db")
        val fileName = target.driveFileName

        try {
            android.util.Log.d(TAG, "開始備份 ${target.databaseName} → $fileName (診斷模式)")

            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")

            android.util.Log.d(TAG, "[本地診斷] 初始大小 - DB: ${dbFile.length()} bytes, WAL: ${walFile.length()} bytes, SHM: ${shmFile.length()} bytes")

            // 1. 強制執行 Checkpoint 並檢查結果
            DatabaseSnapshot.checkpoint(target.database)

            android.util.Log.d(TAG, "[本地診斷] Checkpoint 後大小 - DB: ${dbFile.length()} bytes, WAL: ${walFile.length()} bytes")

            if (!dbFile.exists() || dbFile.length() <= 4096L) {
                android.util.Log.w(TAG, "警告：資料庫檔案過小 (${dbFile.length()} bytes)，可能未包含實際資料")
            }

            // 2. 建立本地快照
            DatabaseSnapshot.copy(dbFile, backupTempFile)

            // 3. 搜尋現有檔案
            val listResult = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$fileName' and trashed = false")
                .setFields("files(id, name, size)")
                .execute()

            val existingFile = listResult.files?.firstOrNull()
            val mediaContent = FileContent("application/x-sqlite3", backupTempFile)
            val fileSizeInKb = backupTempFile.length() / 1024.0

            if (existingFile != null) {
                // 路徑 A：檔案已存在，執行覆蓋上傳 (Update)
                android.util.Log.d(TAG, "[G-Drive] Current Target File ID: ${existingFile.id} (執行更新, 大小: %.2f KB)".format(fileSizeInKb))
                driveService.files().update(existingFile.id, null, mediaContent).execute()

                // 清除可能重複的同名檔案 (防呆)
                if ((listResult.files?.size ?: 0) > 1) {
                    listResult.files.drop(1).forEach { extraFile ->
                        try {
                            driveService.files().delete(extraFile.id).execute()
                            android.util.Log.d(TAG, "已清理重複的備份檔: ${extraFile.id}")
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
            } else {
                // 路徑 B：檔案不存在，執行新建 (Create)
                android.util.Log.d(TAG, "雲端查無備份 $fileName，執行新建 (大小: %.2f KB)...".format(fileSizeInKb))
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf("appDataFolder")
                }
                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                android.util.Log.d(TAG, "[G-Drive] New Target File ID: ${uploadedFile.id}")
            }

            // 4. 強制同步確認 (讀取最新修改時間)
            val updatedFile = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$fileName' and trashed = false")
                .setFields("files(id, modifiedTime, size)")
                .execute()
                .files?.firstOrNull()

            val updatedSizeInKb = (updatedFile?.getSize() ?: 0L) / 1024.0
            android.util.Log.d(TAG, "備份成功 $fileName。雲端最後修改時間: ${updatedFile?.modifiedTime}, 雲端檔案大小: %.2f KB".format(updatedSizeInKb))
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "備份異常 ($fileName)", e)
            return false
        } finally {
            if (backupTempFile.exists()) backupTempFile.delete()
        }
    }

    /**
     * 還原所有 BackupTarget：全部先下載到暫存資料夾 → RestorePlanner 全部檢查通過 → 才開始覆蓋。
     * 只找自己變體的 driveFileName；找不到不會退回去讀其他檔名。
     * 成功時會重啟 App 進程。
     */
    suspend fun restoreDatabase(account: GoogleSignInAccount): RestoreResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, RESTORE_TEMP_DIR)
        try {
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            val driveService = getDriveService(account)

            // 1. 全部下載到暫存資料夾（雲端沒有的檔案 downloaded = null）
            val items = backupTargets.map { target ->
                RestoreItem(
                    fileName = target.driveFileName,
                    downloaded = download(driveService, target.driveFileName, File(tempDir, target.driveFileName)),
                    localDbFile = context.getDatabasePath(target.databaseName),
                    currentVersion = target.database.openHelper.readableDatabase.version,
                    required = target.required
                )
            }

            // 2. 全部檢查通過才關閉資料庫並覆蓋
            val result = RestorePlanner.restore(items) {
                backupTargets.forEach { it.database.close() }
            }.let { if (it is RestoreResult.MissingRequired) it.copy(source = BackupSource.CLOUD) else it }

            if (result !is RestoreResult.Success) {
                android.util.Log.e(TAG, "還原中止，本機未變更: $result")
                return@withContext result
            }

            android.util.Log.d(TAG, "資料庫替換完成，準備重啟進程")

            withContext(Dispatchers.Main) {
                Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
                delay(500)
                AppRestarter.restart(context)
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "還原異常", e)
            RestoreResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** 下載 appDataFolder 中最新的同名檔；雲端沒有此檔回傳 null */
    private fun download(driveService: Drive, fileName: String, dest: File): File? {
        val driveFile = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$fileName' and trashed = false")
            .setOrderBy("modifiedTime desc")
            .setPageSize(1)
            .setFields("files(id, name, size, modifiedTime)")
            .execute()
            .files?.firstOrNull()

        if (driveFile == null) {
            android.util.Log.w(TAG, "雲端找不到備份檔案 ($fileName)")
            return null
        }

        android.util.Log.d(TAG, "[G-Drive] Selected Restore File ID: ${driveFile.id}, Name: $fileName, Date: ${driveFile.modifiedTime}, Size: ${driveFile.size}")

        FileOutputStream(dest).use { outputStream ->
            driveService.files().get(driveFile.id).executeMediaAndDownloadTo(outputStream)
            outputStream.flush()
            outputStream.channel.force(true)
        }
        if (dest.length() == 0L) throw Exception("下載檔案為空 ($fileName)")
        return dest
    }
}
