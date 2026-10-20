package com.example.stock.core.data.source

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwStockRemoteDataSource @Inject constructor(private val client: OkHttpClient) {

    fun fetchTwseListed(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val request = Request.Builder()
                .url("https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONArray(response.body?.string())
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val code = item.optString("Code")
                    val name = item.optString("Name")
                    if (code.isNotEmpty()) map[code] = name
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return map
    }

    fun fetchTpexOtc(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val request = Request.Builder()
                .url("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONArray(response.body?.string())
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val code = item.optString("SecuritiesCompanyCode")
                    val name = item.optString("CompanyName")
                    if (code.isNotEmpty()) map[code] = name
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return map
    }
}