package com.example.stock.core.data.source

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class TwStockCacheData(
    val nameMap: Map<String, String>,
    val typeMap: Map<String, String>
)

@Singleton
class TwStockLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("StockCachePrefs", Context.MODE_PRIVATE)

    fun getLastFetchDate(): String? = prefs.getString("LAST_FETCH_DATE", "")

    fun setLastFetchDate(date: String) {
        prefs.edit { putString("LAST_FETCH_DATE", date) }
    }

    fun save(nameMap: Map<String, String>, typeMap: Map<String, String>) {
        prefs.edit {
            putString("CACHE_NAMES", JSONObject(nameMap).toString())
                .putString("CACHE_TYPES", JSONObject(typeMap).toString())
        }
    }

    fun load(): TwStockCacheData? {
        val nameJsonStr = prefs.getString("CACHE_NAMES", null) ?: return null
        val typeJsonStr = prefs.getString("CACHE_TYPES", null) ?: return null

        return try {
            val nameJson = JSONObject(nameJsonStr)
            val typeJson = JSONObject(typeJsonStr)
            val nameMap = mutableMapOf<String, String>()
            val typeMap = mutableMapOf<String, String>()
            nameJson.keys().forEach { key -> nameMap[key] = nameJson.getString(key) }
            typeJson.keys().forEach { key -> typeMap[key] = typeJson.getString(key) }
            TwStockCacheData(nameMap, typeMap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}