package com.example.stock.core.util

/** 所有變體 applicationId 的共同開頭 */
private const val BASE_APPLICATION_ID = "com.example.stock"

/**
 * Drive appDataFolder 中的備份檔名，依 applicationId 區分。
 *
 * 四個變體共用同一個 Google Cloud 專案，appDataFolder 也共用；
 * 檔名不同才不會互相覆蓋。後綴 = applicationId 去掉 "com.example.stock"：
 * - com.example.stock                → baseName.db（basicRelease，與舊版相同，舊備份才還原得回來）
 * - com.example.stock.debug          → baseName.debug.db
 * - com.example.stock.personal       → baseName.personal.db
 * - com.example.stock.personal.debug → baseName.personal.debug.db
 *
 * 呼叫時傳 BuildConfig.APPLICATION_ID，不要在各 flavor 寫死檔名。
 *
 * @throws IllegalArgumentException applicationId 不是 com.example.stock 或 com.example.stock.xxx
 */
fun driveFileNameFor(baseName: String, applicationId: String): String {
    require(applicationId == BASE_APPLICATION_ID || applicationId.startsWith("$BASE_APPLICATION_ID.")) {
        "applicationId 必須是 $BASE_APPLICATION_ID 或以 \"$BASE_APPLICATION_ID.\" 開頭：$applicationId"
    }
    val suffix = applicationId.removePrefix(BASE_APPLICATION_ID)
    return "$baseName$suffix.db"
}
