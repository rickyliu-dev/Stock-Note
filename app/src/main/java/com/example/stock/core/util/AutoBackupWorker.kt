package com.example.stock.core.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stock.core.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 定期自動備份到 Google Drive (由 AutoBackupScheduler 排程)
 * 使用雲端備份頁登入時記下的 Google 帳號，未登入或授權需要使用者確認時直接略過
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
        val email = settingsRepository.googleDriveAccountFlow.first()?.email
        if (email == null) {
            Log.d(TAG, "尚未登入 Google 帳號，略過自動備份")
            return Result.success()
        }

        val accessToken = when (val auth = googleDriveManager.requestAuthorization(email)) {
            is DriveAuthResult.Granted -> auth.accessToken
            is DriveAuthResult.NeedsResolution -> {
                // 背景不能跳畫面：授權失效或需要使用者確認時安靜略過
                Log.d(TAG, "Drive 授權需要使用者確認，略過自動備份")
                return Result.success()
            }
            is DriveAuthResult.Failed -> {
                Log.w(TAG, "取得 Drive 授權失敗: ${auth.message}")
                return retryOrGiveUp()
            }
        }

        return if (googleDriveManager.backupDatabase(accessToken)) {
            settingsRepository.setLastUpdateTimestamp(System.currentTimeMillis())
            Log.d(TAG, "自動備份完成")
            Result.success()
        } else {
            retryOrGiveUp()
        }
    }

    private fun retryOrGiveUp(): Result = if (runAttemptCount < MAX_RETRIES) {
        Log.w(TAG, "自動備份失敗，稍後重試 (第 ${runAttemptCount + 1} 次)")
        Result.retry()
    } else {
        // 本次放棄，下一個週期會再執行
        Log.e(TAG, "自動備份連續失敗，等待下一個週期")
        Result.failure()
    }
}
