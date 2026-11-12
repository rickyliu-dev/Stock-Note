package com.example.stock.core.util

import android.app.PendingIntent

/** 向 Google 要求 Drive appdata 授權的結果 */
sealed class DriveAuthResult {
    /** 已授權，可直接使用 access token */
    data class Granted(val accessToken: String) : DriveAuthResult()

    /** 需要使用者在畫面上選帳號或同意授權（背景執行時應略過） */
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthResult()

    /** 授權失敗（網路、使用者取消等） */
    data class Failed(val message: String) : DriveAuthResult()
}
