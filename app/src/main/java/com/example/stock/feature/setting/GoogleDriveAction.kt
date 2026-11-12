package com.example.stock.feature.setting

/** 雲端備份畫面上需要 Drive 授權的操作；授權完成後依此決定接著做什麼 */
enum class GoogleDriveAction {
    LOGIN,
    BACKUP,
    RESTORE
}
