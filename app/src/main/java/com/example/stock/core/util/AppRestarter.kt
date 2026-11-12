package com.example.stock.core.util

import android.content.Context
import android.content.Intent

/** 還原成功後重啟 App；雲端還原與本地還原共用 */
object AppRestarter {

    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            val restartIntent = Intent.makeRestartActivityTask(intent.component)
            context.startActivity(restartIntent)
        }
        // 徹底殺死進程，確保 Hilt 單例重新建構
        Runtime.getRuntime().exit(0)
    }
}
