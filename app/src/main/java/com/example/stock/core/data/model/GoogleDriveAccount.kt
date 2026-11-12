package com.example.stock.core.data.model

/**
 * 已授權雲端備份的 Google 帳號（由 Drive about.get 取得）。
 * 存在設定中代表「已登入」；登出時清除，背景自動備份就會略過。
 */
data class GoogleDriveAccount(
    val email: String,
    val displayName: String?
)
