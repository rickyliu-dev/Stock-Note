package com.example.stock.core.util

import kotlinx.serialization.Serializable

/**
 * manifest.json 中一個資料庫的資訊。
 *
 * @param databaseName 手機上的資料庫檔名（BackupTarget.databaseName）
 * @param fileName zip 內的檔名
 * @param userVersion 備份當下的 PRAGMA user_version（僅供參考；還原時以 .db 檔頭為準）
 */
@Serializable
data class BackupManifestDatabase(
    val databaseName: String,
    val fileName: String,
    val userVersion: Int
)
