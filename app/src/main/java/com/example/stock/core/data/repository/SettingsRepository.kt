package com.example.stock.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.feature.setting.DashboardSettingItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val TW_FEE_RATE = stringPreferencesKey("tw_fee_rate")
        val TW_DISCOUNT = stringPreferencesKey("tw_discount")
        val TW_MIN_FEE = stringPreferencesKey("tw_min_fee")
        val SHOW_PRE_DEDUCT_COST = booleanPreferencesKey("show_pre_deduct_cost")
        val DASHBOARD_SETTINGS = stringPreferencesKey("dashboard_settings_json")
        val IS_PREMIUM_UNLOCKED = booleanPreferencesKey("is_premium_unlocked")
        val INCLUDE_DIVIDENDS = booleanPreferencesKey("include_dividends")
        val COST_BASIS_METHOD = stringPreferencesKey("cost_basis_method")
        val CURRENT_ACCOUNT_ID = longPreferencesKey("current_account_id")
        val IS_CASH_MANAGEMENT_ENABLED = booleanPreferencesKey("is_cash_management_enabled")
        val LAST_UPDATE_TIMESTAMP = longPreferencesKey("last_update_timestamp")
    }

    val lastUpdateTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_TIMESTAMP] ?: 0L
    }

    suspend fun setLastUpdateTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_UPDATE_TIMESTAMP] = timestamp
        }
    }

    val currentAccountIdFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[CURRENT_ACCOUNT_ID] ?: 1L
    }

    suspend fun setCurrentAccountId(id: Long) {
        dataStore.edit { preferences ->
            preferences[CURRENT_ACCOUNT_ID] = id
        }
    }

    data class TwSettings(
        val feeRate: String = MarketConstants.Taiwan.DEFAULT_FEE_RATE,
        val discount: String = MarketConstants.Taiwan.DISCOUNT,
        val minFee: String = MarketConstants.Taiwan.DEFAULT_MIN_FEE,
        val showPreDeduct: Boolean = false
    )

    val isPremiumUnlockedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_PREMIUM_UNLOCKED] ?: false
    }

    suspend fun unlockPremium() {
        dataStore.edit { preferences ->
            preferences[IS_PREMIUM_UNLOCKED] = true
        }
    }

    // 2. 讀取設定 (以 Flow 形式回傳，當資料改變時 UI 會自動收到通知)
    val twSettingsFlow: Flow<TwSettings> = context.dataStore.data
        .map { preferences ->
            TwSettings(
                preferences[TW_FEE_RATE] ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE,
                preferences[TW_DISCOUNT] ?: MarketConstants.Taiwan.DISCOUNT,
                preferences[TW_MIN_FEE] ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE,
                showPreDeduct = preferences[SHOW_PRE_DEDUCT_COST] ?: false
            )
        }

    // 3. 儲存設定 (使用 suspend 確保非同步執行)
    suspend fun saveTwSettings(feeRate: String, discount: String, minFee: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TW_FEE_RATE] = feeRate
            preferences[TW_DISCOUNT] = discount
            preferences[TW_MIN_FEE] = minFee
            preferences[SHOW_PRE_DEDUCT_COST] = enabled
        }
    }

    val dashboardSettingsFlow: Flow<List<DashboardSettingItem>?> = dataStore.data.map { prefs ->
        val json = prefs[DASHBOARD_SETTINGS]
        if (json != null) {
            try {
                Json.decodeFromString<List<DashboardSettingItem>>(json)
            } catch (e: Exception) {
                null // 解析失敗就回傳 null，讓 ViewModel 用預設值
            }
        } else null
    }

    // 🟢 寫入：將 List 轉為 JSON 字串存入
    suspend fun saveDashboardSettings(settings: List<DashboardSettingItem>) {
        val json = Json.encodeToString(settings)
        dataStore.edit { prefs ->
            prefs[DASHBOARD_SETTINGS] = json
        }
    }

    // 🟢 讀取含息開關狀態 (預設為 false，不含息)
    val includeDividendsFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[INCLUDE_DIVIDENDS] ?: false
    }

    // 🟢 切換含息開關
    suspend fun setIncludeDividends(include: Boolean) {
        dataStore.edit { preferences ->
            preferences[INCLUDE_DIVIDENDS] = include
        }
    }

    // 🟢 成本計算方法
    val costBasisMethodFlow: Flow<CostBasisMethod> = dataStore.data.map { preferences ->
        val methodStr = preferences[COST_BASIS_METHOD] ?: CostBasisMethod.AVERAGE_COST.name
        try {
            CostBasisMethod.valueOf(methodStr)
        } catch (e: Exception) {
            CostBasisMethod.AVERAGE_COST
        }
    }

    suspend fun setCostBasisMethod(method: CostBasisMethod) {
        dataStore.edit { preferences ->
            preferences[COST_BASIS_METHOD] = method.name
        }
    }

    // 🟢 現金管理開關
    val isCashManagementEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_CASH_MANAGEMENT_ENABLED] ?: false
    }

    suspend fun setCashManagementEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_CASH_MANAGEMENT_ENABLED] = enabled
        }
    }
}