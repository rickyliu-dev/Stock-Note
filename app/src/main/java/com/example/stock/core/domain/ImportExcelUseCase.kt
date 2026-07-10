package com.example.stock.core.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import kotlinx.datetime.toLocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * 負責解析匯入的 Excel 檔案
 */
class ImportExcelUseCase @Inject constructor() {

    fun execute(context: Context, uri: Uri): List<TransactionItem> {
        val transactions = mutableListOf<TransactionItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val workbook = XSSFWorkbook(stream)
                val sheet = workbook.getSheetAt(0)
                val rowIterator = sheet.iterator()

                // 跳過標題列
                if (rowIterator.hasNext()) rowIterator.next()

                while (rowIterator.hasNext()) {
                    val row = rowIterator.next()
                    try {
                        // 1. 日期 (處理可能是 Date 型別或 String 型別)
                        val dateCell = row.getCell(0)
                        val dateStr = if (dateCell?.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                            dateFormat.format(dateCell.dateCellValue)
                        } else {
                            dateCell?.toString()?.trim() ?: ""
                        }

                        // 2. 代號
                        val symbol = row.getCell(1)?.let { getCellStringValue(it) } ?: ""
                        
                        // 3. 名稱
                        val name = row.getCell(2)?.let { getCellStringValue(it) } ?: ""
                        
                        // 4. 類型
                        val typeStr = row.getCell(3)?.toString()?.trim() ?: "BUY"
                        
                        // 5. 價格 (Numeric)
                        val price = row.getCell(4)?.numericCellValue ?: 0.0
                        
                        // 6. 股數 (Numeric)
                        val shares = row.getCell(5)?.numericCellValue?.toInt() ?: 0
                        
                        // 7. 手續費
                        val fee = row.getCell(6)?.numericCellValue ?: 0.0
                        
                        // 8. 稅金
                        val tax = row.getCell(7)?.numericCellValue ?: 0.0
                        
                        // 10. 備註
                        val note = row.getCell(9)?.toString() ?: ""

                        if (dateStr.isNotBlank() && symbol.isNotBlank()) {
                            val transType = mapType(typeStr)
                            
                            // 解析日期，確保符合 ISO 格式 YYYY-MM-DD
                            val finalDate = try {
                                dateStr.substringBefore(" ").substringBefore("T").toLocalDate()
                            } catch (e: Exception) {
                                Log.e("ImportExcel", "日期解析失敗: $dateStr", e)
                                null
                            }

                            if (finalDate != null) {
                                // 自動計算總額
                                val calculatedTotal = when (transType) {
                                    TransactionType.BUY -> (price * shares) + fee
                                    TransactionType.SELL -> (price * shares) - fee - tax
                                    TransactionType.DIVIDEND -> (price * shares) - fee
                                    TransactionType.DEPOSIT, TransactionType.ADJUSTMENT -> price
                                    TransactionType.WITHDRAW -> -price
                                    else -> price * shares
                                }

                                transactions.add(
                                    TransactionItem(
                                        id = 0L,
                                        type = transType,
                                        symbol = symbol,
                                        name = name,
                                        price = price,
                                        shares = shares,
                                        date = finalDate,
                                        fee = fee,
                                        tax = tax,
                                        note = note,
                                        dividend = if (transType == TransactionType.DIVIDEND) price * shares else 0.0,
                                        total = calculatedTotal,
                                        participatingShares = if (transType == TransactionType.STOCK_DIVIDEND || transType == TransactionType.SPLIT || transType == TransactionType.CAPITAL_REDUCTION) shares else 0
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ImportExcel", "解析列出錯: ${row.rowNum}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImportExcel", "讀取 Excel 檔案出錯", e)
            throw e // 丟出去讓 ViewModel 捕獲並顯示 Toast
        }
        return transactions
    }

    private fun getCellStringValue(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                // 如果代號被 Excel 當成數字，去掉小數點 (.0)
                val value = cell.numericCellValue
                if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            else -> cell.toString()
        }.trim()
    }

    private fun mapType(type: String): TransactionType {
        return when (type.uppercase()) {
            "買入", "BUY" -> TransactionType.BUY
            "賣出", "SELL" -> TransactionType.SELL
            "股息", "DIVIDEND" -> TransactionType.DIVIDEND
            "配股", "STOCK_DIVIDEND" -> TransactionType.STOCK_DIVIDEND
            "入金", "DEPOSIT" -> TransactionType.DEPOSIT
            "出金", "WITHDRAW" -> TransactionType.WITHDRAW
            "減資", "CAPITAL_REDUCTION" -> TransactionType.CAPITAL_REDUCTION
            "分割", "SPLIT" -> TransactionType.SPLIT
            else -> TransactionType.BUY
        }
    }
}
