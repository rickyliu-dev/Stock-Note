package com.example.stock.core.data.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDate

/**
 * 用於 UI 顯示的交易資料類別 (與 DB Entity 隔離)
 */
@Serializable
data class TransactionItem(
    val id: Long,
    val accountId: Long = 1L,
    val type: TransactionType,
    val symbol: String,
    val name: String,
    val price: Double,
    val shares: Int,
    val multiplier: Double,
    val date: LocalDate,
    val fee: Double,
    val note: String,
    val dividend: Double,
    val total: Double,
    val participatingShares: Int
)

/**
 * 將資料庫 Entity 轉換為 UI Model，以及反向轉換
 */
object TransactionMapper {
    fun toItem(entity: Transaction): TransactionItem {
        return TransactionItem(
            id = entity.id,
            accountId = entity.accountId,
            type = entity.type,
            symbol = entity.symbol,
            name = entity.name,
            price = entity.price,
            shares = entity.shares,
            multiplier = entity.multiplier,
            date = try {
                LocalDate.parse(entity.date)
            } catch (e: Exception) {
                LocalDate(1970, 1, 1) // 或者其他預設值
            },
            fee = entity.fee,
            note = entity.note,
            dividend = entity.dividend,
            total = entity.total,
            participatingShares = entity.participatingShares
        )
    }

    fun toEntity(item: TransactionItem): Transaction {
        return Transaction(
            id = item.id,
            accountId = item.accountId,
            type = item.type,
            symbol = item.symbol,
            name = item.name,
            price = item.price,
            shares = item.shares,
            multiplier = item.multiplier,
            date = item.date.toString(), // LocalDate.toString() 預設就是 ISO 格式
            fee = item.fee,
            note = item.note,
            dividend = item.dividend,
            total = item.total,
            participatingShares = item.participatingShares
        )
    }
}
