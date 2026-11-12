package com.example.stock.core.util

/** 還原的來源，用來讓錯誤訊息說清楚是在哪裡找不到檔案 */
enum class BackupSource {
    /** Google Drive appDataFolder（各變體只讀自己的 driveFileName） */
    CLOUD,

    /** 使用者選的本地備份 zip */
    FILE
}
