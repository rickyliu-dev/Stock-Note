package com.example.stock.core.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.stock.core.data.model.GoogleDriveAccount
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }

    private val authorizationClient = Identity.getAuthorizationClient(context)

    private fun authorizationRequest(email: String?): AuthorizationRequest =
        AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
            .apply { if (email != null) setAccount(Account(email, GOOGLE_ACCOUNT_TYPE)) }
            .build()

    /**
     * 要求 Drive appdata 授權。
     * email 為 null 時由 Google 決定帳號（必要時會回傳 NeedsResolution 讓使用者選帳號）；
     * 已授權過的帳號會直接回傳 Granted，不會跳畫面。
     */
    suspend fun requestAuthorization(email: String?): DriveAuthResult = try {
        authorizationClient.authorize(authorizationRequest(email)).await().toDriveAuthResult()
    } catch (e: Exception) {
        android.util.Log.e(TAG, "要求 Drive 授權失敗", e)
        DriveAuthResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** 解析授權畫面（NeedsResolution 的 PendingIntent）回傳的結果 */
    fun authorizationResultFromIntent(data: Intent?): DriveAuthResult {
        if (data == null) return DriveAuthResult.Failed("未取得授權結果")
        return try {
            authorizationClient.getAuthorizationResultFromIntent(data).toDriveAuthResult()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "解析 Drive 授權結果失敗", e)
            DriveAuthResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 用 access token 查詢授權的是哪個 Google 帳號 */
    suspend fun fetchAccount(accessToken: String): GoogleDriveAccount = withContext(Dispatchers.IO) {
        val user = getDriveService(accessToken).about().get()
            .setFields("user(emailAddress,displayName)")
            .execute()
            .user
        GoogleDriveAccount(email = user.emailAddress, displayName = user.displayName)
    }

    fun getDriveService(accessToken: String): Drive {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            requestInitializer
        ).setApplicationName("Stock App").build()
    }

    /**
     * 逐一備份所有 BackupTarget，任何一個失敗就回傳 false（其餘仍會嘗試備份）。
     * 每個資料庫採搜尋並覆蓋 (Search-and-Update) 模式，
     * 確保 appDataFolder 中每個 driveFileName 永遠只有一個檔案 ID。
     */
    suspend fun backupDatabase(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        val driveService = try {
            getDriveService(accessToken)
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
    suspend fun restoreDatabase(accessToken: String): RestoreResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, RESTORE_TEMP_DIR)
        try {
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            val driveService = getDriveService(accessToken)

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

private fun AuthorizationResult.toDriveAuthResult(): DriveAuthResult {
    val pending = pendingIntent
    val token = accessToken
    return when {
        hasResolution() && pending != null -> DriveAuthResult.NeedsResolution(pending)
        token != null -> DriveAuthResult.Granted(token)
        else -> DriveAuthResult.Failed("未取得 access token")
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
