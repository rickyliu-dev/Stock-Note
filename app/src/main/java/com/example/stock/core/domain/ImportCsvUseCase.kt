package com.example.stock.core.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import kotlinx.datetime.LocalDate
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * 負責解析匯入的 CSV 檔案 (支援 App 匯出格式與 AI 辨識格式)
 */
class ImportCsvUseCase @Inject constructor() {

    fun execute(context: Context, uri: Uri): List<TransactionItem> {
        val transactions = mutableListOf<TransactionItem>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                
                if (lines.isEmpty()) return emptyList()

                // 辨識格式：檢查 Header
                val headerLine = lines[0]
                val header = parseCsvLine(headerLine).map { it.trim().uppercase() }
                
                // 判斷是否為 App 匯出的完整格式 (通常包含 ID 或 ACCOUNTID)
                val isAppExport = header.contains("ID") || header.contains("ACCOUNTID")

                // 跳過第一行 Header
                val dataLines = lines.drop(1)

                dataLines.forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    
                    try {
                        val columns = parseCsvLine(line)
                        
                        // 依照格式進行欄位映射
                        val item = if (isAppExport) {
                            parseAppExportRow(columns)
                        } else {
                            parseAiPromptRow(columns)
                        }

                        if (item != null) {
                            transactions.add(item)
                        } else {
                            // 這裡不再主動拋出 Log.w 以免干擾，若必要的 date 或 symbol 缺失則會回傳 null
                        }
                    } catch (e: Exception) {
                        Log.e("ImportCsv", "解析第 ${index + 2} 行出錯: $line", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImportCsv", "讀取 CSV 檔案出錯", e)
            throw e
        }
        return transactions
    }

    // 解析 App 匯出格式 (14 欄位)
    // ID(0), AccId(1), Type(2), Symbol(3), Name(4), Price(5), Shares(6), Date(7), Fee(8), Tax(9), Note(10), Dividend(11), Total(12), Part(13)
    private fun parseAppExportRow(columns: List<String>): TransactionItem? {
        if (columns.size < 8) return null
        
        val typeStr = columns.getOrNull(2) ?: ""
        val symbol = columns.getOrNull(3) ?: ""
        val name = columns.getOrNull(4)?.trim('\"') ?: ""
        val price = columns.getOrNull(5)?.toDoubleOrNull() ?: 0.0
        val shares = columns.getOrNull(6)?.toIntOrNull() ?: 0
        val dateStr = columns.getOrNull(7) ?: ""
        val fee = columns.getOrNull(8)?.toDoubleOrNull() ?: 0.0
        val tax = columns.getOrNull(9)?.toDoubleOrNull() ?: 0.0
        val note = columns.getOrNull(10)?.trim('\"') ?: ""
        val dividend = columns.getOrNull(11)?.toDoubleOrNull() ?: 0.0
        val total = columns.getOrNull(12)?.toDoubleOrNull() ?: 0.0
        val participatingShares = columns.getOrNull(13)?.toIntOrNull() ?: 0

        val date = parseDate(dateStr) ?: return null
        
        return TransactionItem(
            id = 0L,
            accountId = columns.getOrNull(1)?.toLongOrNull() ?: 1L,
            type = mapType(typeStr),
            symbol = symbol,
            name = name,
            price = price,
            shares = shares,
            date = date,
            fee = fee,
            tax = tax,
            note = note,
            dividend = dividend,
            total = total,
            participatingShares = participatingShares
        )
    }

    // 解析 AI 提示詞格式 (12 欄位)
    // type(0), symbol(1), name(2), price(3), shares(4), date(5), fee(6), tax(7), note(8), dividend(9), total(10), participatingShares(11)
    private fun parseAiPromptRow(columns: List<String>): TransactionItem? {
        // AI 格式至少要有基礎欄位
        if (columns.size < 3) return null
        
        val typeStr = columns[0].trim()
        val symbol = columns[1].trim()
        val name = columns.getOrNull(2)?.trim() ?: ""
        val price = columns.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        val shares = columns.getOrNull(4)?.toIntOrNull() ?: 0
        val dateStr = columns.getOrNull(5)?.trim() ?: ""
        val fee = columns.getOrNull(6)?.toDoubleOrNull() ?: 0.0
        val tax = columns.getOrNull(7)?.toDoubleOrNull() ?: 0.0
        val note = columns.getOrNull(8)?.trim() ?: ""
        val dividendVal = columns.getOrNull(9)?.toDoubleOrNull() ?: 0.0
        val totalVal = columns.getOrNull(10)?.toDoubleOrNull()
        val partShares = columns.getOrNull(11)?.toIntOrNull() ?: 0

        val transType = mapType(typeStr)
        val date = parseDate(dateStr) ?: return null
        
        val finalTotal = totalVal ?: calculateTotal(transType, price, shares, fee, tax)

        return TransactionItem(
            id = 0L,
            type = transType,
            symbol = symbol,
            name = name,
            price = price,
            shares = shares,
            date = date,
            fee = fee,
            tax = tax,
            note = note,
            dividend = if (transType == TransactionType.DIVIDEND && dividendVal == 0.0) price * shares else dividendVal,
            total = finalTotal,
            participatingShares = if (partShares == 0 && (transType == TransactionType.STOCK_DIVIDEND || transType == TransactionType.SPLIT)) shares else partShares
        )
    }

    private fun parseDate(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        return try {
            val normalizedDate = dateStr.replace("/", "-")
            val datePart = normalizedDate.substringBefore(" ").substringBefore("T")
            val parts = datePart.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1].padStart(2, '0')
                val day = parts[2].padStart(2, '0')
                LocalDate.parse("$year-$month-$day")
            } else {
                LocalDate.parse(datePart)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateTotal(type: TransactionType, price: Double, shares: Int, fee: Double, tax: Double): Double {
        return when (type) {
            TransactionType.BUY -> (price * shares) + fee
            TransactionType.SELL -> (price * shares) - fee - tax
            TransactionType.DIVIDEND -> (price * shares) - fee
            TransactionType.DEPOSIT -> price
            TransactionType.WITHDRAW -> -price
            TransactionType.CAPITAL_REDUCTION -> price
            TransactionType.ADJUSTMENT -> price
            TransactionType.STOCK_DIVIDEND, TransactionType.SPLIT -> 0.0
        }
    }

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

    private fun mapType(type: String): TransactionType {
        return when (type.trim().uppercase()) {
            "買入", "BUY" -> TransactionType.BUY
            "賣出", "SELL" -> TransactionType.SELL
            "股息", "DIVIDEND", "現金股息" -> TransactionType.DIVIDEND
            "配股", "STOCK_DIVIDEND", "股票股利" -> TransactionType.STOCK_DIVIDEND
            "入金", "DEPOSIT" -> TransactionType.DEPOSIT
            "出金", "WITHDRAW" -> TransactionType.WITHDRAW
            "減資", "CAPITAL_REDUCTION" -> TransactionType.CAPITAL_REDUCTION
            "分割", "SPLIT", "股票分割" -> TransactionType.SPLIT
            "調整", "ADJUSTMENT" -> TransactionType.ADJUSTMENT
            else -> TransactionType.BUY
        }
    }
}
