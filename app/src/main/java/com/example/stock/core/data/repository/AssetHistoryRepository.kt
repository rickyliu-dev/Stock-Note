package com.example.stock.core.data.repository

import com.example.stock.core.data.model.AssetSnapshot
import com.example.stock.core.data.model.AssetSnapshotDao
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetHistoryRepository @Inject constructor(
    private val dao: AssetSnapshotDao
) {
    fun snapshots(accountId: Long): Flow<List<AssetSnapshot>> = dao.snapshotsByAccount(accountId)

    suspend fun record(accountId: Long, date: LocalDate, totalAssets: Double, cashBalance: Double) {
        dao.upsert(AssetSnapshot(accountId, date.toString(), totalAssets, cashBalance))
    }
}
