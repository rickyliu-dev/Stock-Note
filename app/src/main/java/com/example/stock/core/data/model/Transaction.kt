package com.example.stock.core.data.model

import kotlinx.serialization.Serializable
import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow


@Serializable
enum class TransactionType(val label: String) {
    BUY("買入"),
    SELL("賣出"),
    DIVIDEND("現金股息"),       // 領現金
    STOCK_DIVIDEND("股票股利"), // 領股票 (配股)
    DEPOSIT("入金"),          // 存入現金到帳戶
    WITHDRAW("出金"),         // 從帳戶領出資金
    ADJUSTMENT("調整"),        // 用於退傭、利息支出、差額校正
    CAPITAL_REDUCTION("減資"),  // 退還現金並減少股數
    SPLIT("股票分割");          // 股數增加，股價變小

    // 輔助屬性：定義每個類型的代表色
    fun getColor(): Color {
        return when (this) {
            BUY -> Color(0xFF10B981) // 綠色
            SELL -> Color.Red
            DIVIDEND, STOCK_DIVIDEND -> Color(0xFFFFA000) // 橘色 (股利類)
            DEPOSIT -> Color(0xFF3B82F6) // 藍色 (入金)
            WITHDRAW -> Color(0xFF6B7280) // 灰色 (出金)
            ADJUSTMENT -> Color(0xFF9333EA) // 紫色 (調整)
            CAPITAL_REDUCTION -> Color(0xFFF43F5E) // 玫瑰色
            SPLIT -> Color(0xFF06B6D4) // 青色
        }
    }
}

enum class CostBasisMethod(val label: String) {
    AVERAGE_COST("平均成本法"),
    FIFO("先進先出法");
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val currency: String = "TWD",
    val initialBalance: Double = 0.0,
    val note: String = "",
    val isCashManagementEnabled: Boolean = false
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountId: Long = 1L,
    val type: TransactionType = TransactionType.BUY,
    // 股票代號
    val symbol: String = "",
    // 名稱
    val name: String = "",
    val price: Double = 0.0,
    val shares: Int = 0,
    val multiplier: Double = 1.0,
    val date: String = "",
    // 手續費
    val fee: Double = 0.0,
    val note: String = "",
    // 股息
    val dividend: Double = 0.0,
    // 總金額
    val total: Double = 0.0,
    // 參與除權/除息的股數
    val participatingShares: Int = 0
)

data class StockDetail(
    val currentPrice: Double,
    // 漲跌金額
    val change: Double,
    // 漲跌幅 %
    val changePercent: Double
)

@Entity(tableName = "stock_prices")
data class StockPriceEntity(
    @PrimaryKey val symbol: String,
    val price: Double,
    val change: Double,
    val changePercent: Double
)

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }
}

@Dao
interface TransactionDao {
    // --- 帳戶相關 ---
    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Upsert
    suspend fun upsertAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getAccountFlowById(id: Long): Flow<Account?>

    // --- 交易相關 ---
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>>

    @Upsert
    suspend fun upsertTransactions(transactions: List<Transaction>)

    // 給清單用的：最新日期在最上面
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    // 給資產曲線計算用的：最早日期在最前面
    @Query("SELECT * FROM transactions ORDER BY date ASC")
    fun getAllTransactionsAsc(): Flow<List<Transaction>>

    @Upsert
    suspend fun upsertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // 自動計算總資產（考慮乘數），這就是你擔心的效能解藥！
    @Query("SELECT SUM(price * shares * multiplier) FROM transactions WHERE type = 'BUY'")
    fun getTotalMarketValue(): Flow<Double?>

    // 一次性取得所有清單（不使用 Flow）
    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<Transaction>

    // 存入股價 (如果代號一樣就覆蓋更新現價)
    @Upsert
    suspend fun insertStockPrices(prices: List<StockPriceEntity>)

    // 讀取所有股價
    @Query("SELECT * FROM stock_prices")
    suspend fun getAllCachedPrices(): List<StockPriceEntity>

    @Query("SELECT * FROM stock_prices")
    fun getAllCachedPricesFlow(): Flow<List<StockPriceEntity>>

    // 根據ID取得對應的交易資料
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    // 單筆刪除 (給編輯頁面用)
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 批次刪除 (給長按多選用)
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}