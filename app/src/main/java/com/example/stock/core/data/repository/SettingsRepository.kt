package com.example.stock.core.data.repository

import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.model.AutoBackupInterval
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.DashboardSettingItem
import com.example.stock.core.data.model.GoogleDriveAccount
import com.example.stock.core.data.model.HomeSort
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    data class TwSettings(
        val feeRate: String = MarketConstants.Taiwan.DEFAULT_FEE_RATE,
        val discount: String = MarketConstants.Taiwan.DISCOUNT,
        val minFee: String = MarketConstants.Taiwan.DEFAULT_MIN_FEE,
        val showPreDeduct: Boolean = false
    )

    val lastUpdateTimestampFlow: Flow<Long>
    suspend fun setLastUpdateTimestamp(timestamp: Long)

    val currentAccountIdFlow: Flow<Long>
    suspend fun setCurrentAccountId(id: Long)

    val twSettingsFlow: Flow<TwSettings>
    suspend fun saveTwSettings(feeRate: String, discount: String, minFee: String, enabled: Boolean)

    val dashboardSettingsFlow: Flow<List<DashboardSettingItem>?>
    suspend fun saveDashboardSettings(settings: List<DashboardSettingItem>)

    val includeDividendsFlow: Flow<Boolean>
    suspend fun setIncludeDividends(include: Boolean)

    val costBasisMethodFlow: Flow<CostBasisMethod>
    suspend fun setCostBasisMethod(method: CostBasisMethod)

    // 交易紀錄卡片是否使用一排三欄的精簡排版
    val compactTransactionCardFlow: Flow<Boolean>
    suspend fun setCompactTransactionCard(compact: Boolean)

    // 自動備份到 Google Drive 的頻率
    val autoBackupIntervalFlow: Flow<AutoBackupInterval>
    suspend fun setAutoBackupInterval(interval: AutoBackupInterval)

    // 已授權雲端備份的 Google 帳號；null 代表未登入
    val googleDriveAccountFlow: Flow<GoogleDriveAccount?>
    suspend fun setGoogleDriveAccount(account: GoogleDriveAccount?)

    // 首頁股票清單的排序方式
    val homeSortFlow: Flow<HomeSort>
    suspend fun setHomeSort(sort: HomeSort)
}