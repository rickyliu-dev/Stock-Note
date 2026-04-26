package com.example.stock.core.api

import com.example.stock.core.data.dataClass.StockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GoogleSheetApi {
    private val client = OkHttpClient()

    // 👇 1. 替換成你剛剛在 GAS 取得的「網頁應用程式網址 (Web App URL)」
    private val scriptUrl = "https://script.google.com/macros/s/AKfycbym3DmIaydwHiNnZXB59zu4eArLZKsfDlAU7__I6siE9LuARn1NcBkpisNIb6Eh96qzqQ/exec"

    // 👇 2. 填入我們剛剛設定的通關密碼
    private val secretToken = "Antony_Super_Secret_888"

    // 使用 suspend function 確保它會在背景執行
    suspend fun backupAllToCloud(stockList: List<StockItem>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 建立最外層的 JSON 物件
                val rootJson = JSONObject()
                rootJson.put("token", secretToken)

                // 2. 建立一個 JSON 陣列，把所有庫存塞進去
                val jsonArray = JSONArray()
                for (stock in stockList) {
                    val stockObj = JSONObject().apply {
                        put("ticker", stock.symbol)
                        put("name", stock.name)
                        put("cost", stock.cost)
                        put("shares", stock.shares)
                    }
                    jsonArray.put(stockObj)
                }

                // 把陣列放入名為 "stocks" 的欄位中
                rootJson.put("stocks", jsonArray)

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = rootJson.toString().toRequestBody(mediaType)

                // 3. 發送請求
                val request = Request.Builder()
                    .url(scriptUrl)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        println("✅ 雲端備份成功: $responseBody")
                        true
                    } else {
                        println("❌ 雲端備份失敗: HTTP 狀態碼 ${response.code}")
                        false
                    }
                }
            } catch (e: Exception) {
                println("❌ 網路連線發生錯誤: ${e.message}")
                false
            }
        }
    }
}