package com.example.stock.core.util

import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import kotlinx.datetime.LocalDate
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * 處理 CSV 匯出與匯入的工具類
 */
object CsvManager {

    // 定義匯出時的標準 Header
    private val EXPORT_HEADER = listOf("ID", "AccountId", "Type", "Symbol", "Name", "Price", "Shares", "Multiplier", "Date", "Fee", "Note", "Dividend", "Total", "ParticipatingShares")

    /**
     * 將交易紀錄匯出為 CSV 字串
     */
    fun exportToCsv(items: List<TransactionItem>): String {
        val sb = StringBuilder()
        sb.append(EXPORT_HEADER.joinToString(",")).append("\n")
        
        items.forEach { item ->
            val row = listOf(
                item.id,
                item.accountId,
                item.type.name,
                item.symbol,
                "\"${item.name}\"", // 處理名稱可能含逗號
                item.price,
                item.shares,
                item.multiplier,
                item.date.toString(),
                item.fee,
                "\"${item.note}\"",
                item.dividend,
                item.total,
                item.participatingShares
            )
            sb.append(row.joinToString(",")).append("\n")
        }
        return sb.toString()
    }

    /**
     * 讀取 CSV 的第一行 (Header)
     * 用於讓使用者進行欄位對應
     */
    fun readHeader(inputStream: InputStream, charset: Charset = Charset.forName("UTF-8")): List<String>? {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, charset))
            val firstLine = reader.readLine()
            firstLine?.split(",")?.map { it.trim().trim('\"') }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根據對應表解析 CSV 內容
     * @param mapping Map<App欄位名, CSV欄位Index>
     * @param customValues Map<App欄位名, 固定值> 如果 CSV 沒這欄位，可以使用固定值
     */
    fun parseCsv(
        inputStream: InputStream,
        mapping: Map<String, Int>,
        customValues: Map<String, String> = emptyMap(),
        charset: Charset = Charset.forName("UTF-8")
    ): List<TransactionItem> {
        val items = mutableListOf<TransactionItem>()
        val reader = BufferedReader(InputStreamReader(inputStream, charset))
        
        // 跳過第一行 Header
        reader.readLine()

        reader.forEachLine { line ->
            val columns = parseCsvLine(line)
            try {
                val item = TransactionItem(
                    id = 0, // 匯入的新資料 ID 設為 0 讓 Room 自動產生
                    accountId = mapping["accountId"]?.let { columns.getOrNull(it)?.toLongOrNull() } 
                        ?: customValues["accountId"]?.toLongOrNull() ?: 1L,
                    type = mapping["type"]?.let { columns.getOrNull(it)?.let { t -> try { TransactionType.valueOf(t) } catch(e: Exception) { null } } } 
                        ?: customValues["type"]?.let { try { TransactionType.valueOf(it) } catch(e: Exception) { null } } ?: TransactionType.BUY,
                    symbol = mapping["symbol"]?.let { columns.getOrNull(it) } 
                        ?: customValues["symbol"] ?: "",
                    name = mapping["name"]?.let { columns.getOrNull(it)?.trim('\"') } 
                        ?: customValues["name"] ?: "",
                    price = mapping["price"]?.let { columns.getOrNull(it)?.toDoubleOrNull() } 
                        ?: customValues["price"]?.toDoubleOrNull() ?: 0.0,
                    shares = mapping["shares"]?.let { columns.getOrNull(it)?.toIntOrNull() } 
                        ?: customValues["shares"]?.toIntOrNull() ?: 0,
                    multiplier = mapping["multiplier"]?.let { columns.getOrNull(it)?.toDoubleOrNull() } 
                        ?: customValues["multiplier"]?.toDoubleOrNull() ?: 1.0,
                    date = mapping["date"]?.let { columns.getOrNull(it)?.let { d -> try { LocalDate.parse(d) } catch(e: Exception) { null } } } 
                        ?: customValues["date"]?.let { try { LocalDate.parse(it) } catch(e: Exception) { null } } ?: LocalDate(1970, 1, 1),
                    fee = mapping["fee"]?.let { columns.getOrNull(it)?.toDoubleOrNull() } 
                        ?: customValues["fee"]?.toDoubleOrNull() ?: 0.0,
                    note = mapping["note"]?.let { columns.getOrNull(it)?.trim('\"') } 
                        ?: customValues["note"] ?: "",
                    dividend = mapping["dividend"]?.let { columns.getOrNull(it)?.toDoubleOrNull() } 
                        ?: customValues["dividend"]?.toDoubleOrNull() ?: 0.0,
                    total = mapping["total"]?.let { columns.getOrNull(it)?.toDoubleOrNull() } 
                        ?: customValues["total"]?.toDoubleOrNull() ?: 0.0,
                    participatingShares = mapping["participatingShares"]?.let { columns.getOrNull(it)?.toIntOrNull() } 
                        ?: customValues["participatingShares"]?.toIntOrNull() ?: 0
                )
                items.add(item)
            } catch (e: Exception) {
                // 略過解析失敗的行數，實際開發建議記錄錯誤
            }
        }
        return items
    }

    /**
     * 簡單處理 CSV 行解析，考慮引號包裹的情況
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString().trim())
                cur = StringBuilder()
            } else {
                cur.append(ch)
            }
        }
        result.add(cur.toString().trim())
        return result
    }
}
