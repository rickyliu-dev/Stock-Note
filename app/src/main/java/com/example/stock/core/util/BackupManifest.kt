package com.example.stock.core.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地備份 zip 內的 manifest.json。
 *
 * @param formatVersion zip 格式版本；比 App 支援的新就拒絕還原
 * @param appVersionName 建立備份的 App versionName
 * @param createdAt 建立時間（ISO-8601，例如 2026-11-11T08:30:00Z）
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val createdAt: String,
    val databases: List<BackupManifestDatabase>
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FILE_NAME = "manifest.json"
        const val CURRENT_FORMAT_VERSION = 1

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true // 之後加欄位，舊版 App 仍讀得懂
        }

        /** 解析失敗回傳 null */
        fun fromJson(text: String): BackupManifest? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
