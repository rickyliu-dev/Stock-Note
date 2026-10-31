package com.example.stock.core.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stock.core.data.repository.SettingsRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 定期自動備份到 Google Drive (由 AutoBackupScheduler 排程)
 * 使用上次登入的 Google 帳號，未登入或未授權時直接略過
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val googleDriveManager: GoogleDriveManager,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
        if (account == null || !GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            Log.d(TAG, "尚未登入 Google 帳號，略過自動備份")
            return Result.success()
        }

        return if (googleDriveManager.backupDatabase(account)) {
            settingsRepository.setLastUpdateTimestamp(System.currentTimeMillis())
            Log.d(TAG, "自動備份完成")
            Result.success()
        } else if (runAttemptCount < MAX_RETRIES) {
            Log.w(TAG, "自動備份失敗，稍後重試 (第 ${runAttemptCount + 1} 次)")
            Result.retry()
        } else {
            // 本次放棄，下一個週期會再執行
            Log.e(TAG, "自動備份連續失敗，等待下一個週期")
            Result.failure()
        }
    }
}
