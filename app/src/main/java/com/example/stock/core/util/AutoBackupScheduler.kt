package com.example.stock.core.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.stock.core.data.model.AutoBackupInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 依設定的頻率排程 / 取消自動備份
 * WorkManager 會保存排程，重開機或關閉 App 後仍會繼續執行
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val WORK_NAME = "auto_backup"
    }

    fun schedule(interval: AutoBackupInterval) {
        val workManager = WorkManager.getInstance(context)

        if (interval == AutoBackupInterval.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        // 有網路且電量不低時才執行，避免耗電與失敗
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(interval.days.toLong(), TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        // UPDATE：已有排程時直接套用新的頻率
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
