package com.example.stock.core.data.repository

import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.feature.setting.DashboardSettingItem
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

    val isPremiumUnlockedFlow: Flow<Boolean>
    suspend fun unlockPremium()

    val twSettingsFlow: Flow<SettingsRepositoryImpl.TwSettings>
    suspend fun saveTwSettings(feeRate: String, discount: String, minFee: String, enabled: Boolean)

    val dashboardSettingsFlow: Flow<List<DashboardSettingItem>?>
    suspend fun saveDashboardSettings(settings: List<DashboardSettingItem>)

    val includeDividendsFlow: Flow<Boolean>
    suspend fun setIncludeDividends(include: Boolean)

    val costBasisMethodFlow: Flow<CostBasisMethod>
    suspend fun setCostBasisMethod(method: CostBasisMethod)

    val isCashManagementEnabledFlow: Flow<Boolean>
    suspend fun setCashManagementEnabled(enabled: Boolean)
}