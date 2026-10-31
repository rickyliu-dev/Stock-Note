package com.example.stock.core.data.model

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * 每個帳戶每天一筆的總資產紀錄，供資產走勢圖使用
 * 同一天重複記錄時會覆蓋，保留當天最後一次計算的結果
 */
@Entity(
    tableName = "asset_snapshots",
    primaryKeys = ["accountId", "date"],
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
data class AssetSnapshot(
    val accountId: Long,
    // ISO 格式 (yyyy-MM-dd)，與交易紀錄的日期格式相同，字串比較即可排序
    val date: String,
    val totalAssets: Double,
    val cashBalance: Double
)

@Dao
interface AssetSnapshotDao {
    @Upsert
    suspend fun upsert(snapshot: AssetSnapshot)

    @Query("SELECT * FROM asset_snapshots WHERE accountId = :accountId ORDER BY date ASC")
    fun snapshotsByAccount(accountId: Long): Flow<List<AssetSnapshot>>
}

/** 資產走勢圖的時間範圍 */
enum class AssetHistoryRange(val label: String, private val period: DatePeriod?) {
    WEEK("1W", DatePeriod(days = 7)),
    MONTH("1M", DatePeriod(months = 1)),
    YEAR("1Y", DatePeriod(years = 1)),
    ALL("ALL", null);

    /** 只保留範圍內的紀錄 (snapshots 需依日期由舊到新排序) */
    fun filter(snapshots: List<AssetSnapshot>, today: LocalDate): List<AssetSnapshot> {
        val start = period?.let { today.minus(it).toString() } ?: return snapshots
        return snapshots.filter { it.date >= start }
    }
}
