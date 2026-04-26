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
    private val database: com.example.stock.dataBase.StockDatabase
) {
    companion object {
        private const val BACKUP_FILE_NAME = "stock_ledger.db"
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
            // 同時清除快取中的認證資訊 (如果有需要)
            android.util.Log.d("GoogleDriveManager", "Google 帳號已成功登出")
            true
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "Google 登出失敗", e)
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
     * 搜尋並覆蓋 (Search-and-Update) 模式
     * 確保 appDataFolder 永遠只有一個名為 stock_ledger.db 的檔案 ID
     */
    suspend fun backupDatabase(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("stock_database")
        val backupTempFile = File(context.cacheDir, "backup_ready.db")

        try {
            android.util.Log.d("GoogleDriveManager", "開始備份流程 (診斷模式)")

            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")
            
            android.util.Log.d("GoogleDriveManager", "[本地診斷] 初始大小 - DB: ${dbFile.length()} bytes, WAL: ${walFile.length()} bytes, SHM: ${shmFile.length()} bytes")

            // 1. 強制執行 Checkpoint 並檢查結果
            try {
                val sdb = database.openHelper.writableDatabase
                val cursor = sdb.query("PRAGMA wal_checkpoint(TRUNCATE)")
                if (cursor.moveToFirst()) {
                    val busy = cursor.getInt(0)
                    val log = cursor.getInt(1)
                    val checkpointed = cursor.getInt(2)
                    android.util.Log.d("GoogleDriveManager", "[Checkpoint 詳情] Busy: $busy, Log Frames: $log, Checkpointed: $checkpointed")
                }
                cursor.close()
            } catch (e: Exception) {
                android.util.Log.e("GoogleDriveManager", "Checkpoint 執行異常: ${e.message}")
            }

            android.util.Log.d("GoogleDriveManager", "[本地診斷] Checkpoint 後大小 - DB: ${dbFile.length()} bytes, WAL: ${walFile.length()} bytes")

            if (!dbFile.exists() || dbFile.length() <= 4096L) {
                android.util.Log.w("GoogleDriveManager", "警告：資料庫檔案過小 (${dbFile.length()} bytes)，可能未包含實際資料")
            }

            // 2. 建立本地快照
            java.io.FileInputStream(dbFile).use { input ->
                FileOutputStream(backupTempFile).use { output ->
                    input.channel.transferTo(0, input.channel.size(), output.channel)
                    output.flush()
                    output.channel.force(true)
                }
            }

            val driveService = getDriveService(account)

            // 3. 搜尋現有檔案
            val listResult = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
                .setFields("files(id, name, size)")
                .execute()

            val existingFile = listResult.files?.firstOrNull()
            val mediaContent = FileContent("application/x-sqlite3", backupTempFile)
            val fileSizeInKb = backupTempFile.length() / 1024.0

            if (existingFile != null) {
                // 路徑 A：檔案已存在，執行覆蓋上傳 (Update)
                android.util.Log.d("GoogleDriveManager", "[G-Drive] Current Target File ID: ${existingFile.id} (執行更新, 大小: %.2f KB)".format(fileSizeInKb))
                driveService.files().update(existingFile.id, null, mediaContent).execute()
                
                // 清除可能重複的同名檔案 (防呆)
                if ((listResult.files?.size ?: 0) > 1) {
                    listResult.files.drop(1).forEach { extraFile ->
                        try {
                            driveService.files().delete(extraFile.id).execute()
                            android.util.Log.d("GoogleDriveManager", "已清理重複的備份檔: ${extraFile.id}")
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
            } else {
                // 路徑 B：檔案不存在，執行新建 (Create)
                android.util.Log.d("GoogleDriveManager", "雲端查無備份，執行新建 (大小: %.2f KB)...".format(fileSizeInKb))
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                android.util.Log.d("GoogleDriveManager", "[G-Drive] New Target File ID: ${uploadedFile.id}")
            }

            // 4. 強制同步確認 (讀取最新修改時間)
            val updatedFile = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
                .setFields("files(id, modifiedTime, size)")
                .execute()
                .files?.firstOrNull()

            val updatedSizeInKb = (updatedFile?.getSize() ?: 0L) / 1024.0
            android.util.Log.d("GoogleDriveManager", "備份成功。雲端最後修改時間: ${updatedFile?.modifiedTime}, 雲端檔案大小: %.2f KB".format(updatedSizeInKb))
            true
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "備份異常", e)
            false
        } finally {
            if (backupTempFile.exists()) backupTempFile.delete()
        }
    }

    /**
     * ID 精確比對還原模式
     */
    suspend fun restoreDatabase(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_restore.db")
        val dbFile = context.getDatabasePath("stock_database")
        try {
            val driveService = getDriveService(account)
            
            // 1. 搜尋並取得最新備份
            val listResult = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setPageSize(1)
                .setFields("files(id, name, size, modifiedTime)")
                .execute()

            val driveFile = listResult.files?.firstOrNull()

            if (driveFile == null) {
                android.util.Log.e("GoogleDriveManager", "雲端找不到備份檔案 ($BACKUP_FILE_NAME)")
                return@withContext false
            }

            android.util.Log.d("GoogleDriveManager", "[G-Drive] Selected Restore File ID: ${driveFile.id}, Date: ${driveFile.modifiedTime}, Size: ${driveFile.size}")

            // 2. 下載到暫存區
            FileOutputStream(tempFile).use { outputStream ->
                driveService.files().get(driveFile.id).executeMediaAndDownloadTo(outputStream)
                outputStream.flush()
                outputStream.channel.force(true)
            }

            if (tempFile.length() == 0L) throw Exception("下載檔案為空")

            // 3. 本地環境淨化：物理刪除 .db, -wal, -shm
            database.close()
            
            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")
            
            listOf(dbFile, walFile, shmFile).forEach { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    android.util.Log.d("GoogleDriveManager", "本地淨化: ${file.name}, 成功: $deleted")
                }
            }

            // 4. 覆蓋本地資料庫
            java.io.FileInputStream(tempFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.channel.transferTo(0, input.channel.size(), output.channel)
                    output.flush()
                    output.channel.force(true)
                }
            }

            android.util.Log.d("GoogleDriveManager", "資料庫替換完成，準備重啟進程")

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "還原成功，正在重啟...", Toast.LENGTH_SHORT).show()
                delay(500)
                triggerRestart()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "還原異常", e)
            false
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun triggerRestart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            val restartIntent = Intent.makeRestartActivityTask(intent.component)
            context.startActivity(restartIntent)
        }
        // 徹底殺死進程，確保 Hilt 單例重新建構
        Runtime.getRuntime().exit(0)
    }
}
