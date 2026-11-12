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
import com.example.stock.core.data.model.AutoBackupInterval
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.DashboardSettingItem
import com.example.stock.core.data.model.GoogleDriveAccount
import com.example.stock.core.data.model.HomeSort
import com.example.stock.core.data.model.HomeSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {
    companion object {
        val TW_FEE_RATE = stringPreferencesKey("tw_fee_rate")
        val TW_DISCOUNT = stringPreferencesKey("tw_discount")
        val TW_MIN_FEE = stringPreferencesKey("tw_min_fee")
        val SHOW_PRE_DEDUCT_COST = booleanPreferencesKey("show_pre_deduct_cost")
        val DASHBOARD_SETTINGS = stringPreferencesKey("dashboard_settings_json")
        val INCLUDE_DIVIDENDS = booleanPreferencesKey("include_dividends")
        val COST_BASIS_METHOD = stringPreferencesKey("cost_basis_method")
        val CURRENT_ACCOUNT_ID = longPreferencesKey("current_account_id")
        val LAST_UPDATE_TIMESTAMP = longPreferencesKey("last_update_timestamp")
        val COMPACT_TRANSACTION_CARD = booleanPreferencesKey("compact_transaction_card")
        val AUTO_BACKUP_INTERVAL = stringPreferencesKey("auto_backup_interval")
        val HOME_SORT_ORDER = stringPreferencesKey("home_sort_order")
        val HOME_SORT_DESCENDING = booleanPreferencesKey("home_sort_descending")
        val GOOGLE_DRIVE_EMAIL = stringPreferencesKey("google_drive_email")
        val GOOGLE_DRIVE_DISPLAY_NAME = stringPreferencesKey("google_drive_display_name")
    }

    override val lastUpdateTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_TIMESTAMP] ?: 0L
    }

    override suspend fun setLastUpdateTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_UPDATE_TIMESTAMP] = timestamp
        }
    }

    override val currentAccountIdFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[CURRENT_ACCOUNT_ID] ?: 1L
    }

    override suspend fun setCurrentAccountId(id: Long) {
        dataStore.edit { preferences ->
            preferences[CURRENT_ACCOUNT_ID] = id
        }
    }

    // 2. 讀取設定 (以 Flow 形式回傳，當資料改變時 UI 會自動收到通知)
    override val twSettingsFlow: Flow<SettingsRepository.TwSettings> = context.dataStore.data
        .map { preferences ->
            SettingsRepository.TwSettings(
                preferences[TW_FEE_RATE] ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE,
                preferences[TW_DISCOUNT] ?: MarketConstants.Taiwan.DISCOUNT,
                preferences[TW_MIN_FEE] ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE,
                showPreDeduct = preferences[SHOW_PRE_DEDUCT_COST] ?: false
            )
        }

    // 3. 儲存設定 (使用 suspend 確保非同步執行)
    override suspend fun saveTwSettings(feeRate: String, discount: String, minFee: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TW_FEE_RATE] = feeRate
            preferences[TW_DISCOUNT] = discount
            preferences[TW_MIN_FEE] = minFee
            preferences[SHOW_PRE_DEDUCT_COST] = enabled
        }
    }

    override val dashboardSettingsFlow: Flow<List<DashboardSettingItem>?> = dataStore.data.map { prefs ->
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
    override suspend fun saveDashboardSettings(settings: List<DashboardSettingItem>) {
        val json = Json.encodeToString(settings)
        dataStore.edit { prefs ->
            prefs[DASHBOARD_SETTINGS] = json
        }
    }

    // 🟢 讀取含息開關狀態 (預設為 false，不含息)
    override val includeDividendsFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[INCLUDE_DIVIDENDS] ?: false
    }

    // 🟢 切換含息開關
    override suspend fun setIncludeDividends(include: Boolean) {
        dataStore.edit { preferences ->
            preferences[INCLUDE_DIVIDENDS] = include
        }
    }

    // 🟢 成本計算方法
    override val costBasisMethodFlow: Flow<CostBasisMethod> = dataStore.data.map { preferences ->
        val methodStr = preferences[COST_BASIS_METHOD] ?: CostBasisMethod.AVERAGE_COST.name
        try {
            CostBasisMethod.valueOf(methodStr)
        } catch (e: Exception) {
            CostBasisMethod.AVERAGE_COST
        }
    }

    override suspend fun setCostBasisMethod(method: CostBasisMethod) {
        dataStore.edit { preferences ->
            preferences[COST_BASIS_METHOD] = method.name
        }
    }

    // 🟢 交易紀錄卡片排版
    override val compactTransactionCardFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[COMPACT_TRANSACTION_CARD] ?: false
    }

    override suspend fun setCompactTransactionCard(compact: Boolean) {
        dataStore.edit { preferences ->
            preferences[COMPACT_TRANSACTION_CARD] = compact
        }
    }

    // 🟢 自動備份頻率
    override val autoBackupIntervalFlow: Flow<AutoBackupInterval> = dataStore.data.map { preferences ->
        val intervalStr = preferences[AUTO_BACKUP_INTERVAL] ?: AutoBackupInterval.OFF.name
        try {
            AutoBackupInterval.valueOf(intervalStr)
        } catch (e: Exception) {
            AutoBackupInterval.OFF
        }
    }

    override suspend fun setAutoBackupInterval(interval: AutoBackupInterval) {
        dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_INTERVAL] = interval.name
        }
    }

    // 🟢 雲端備份的 Google 帳號
    override val googleDriveAccountFlow: Flow<GoogleDriveAccount?> = dataStore.data.map { preferences ->
        preferences[GOOGLE_DRIVE_EMAIL]?.let { email ->
            GoogleDriveAccount(email, preferences[GOOGLE_DRIVE_DISPLAY_NAME])
        }
    }

    override suspend fun setGoogleDriveAccount(account: GoogleDriveAccount?) {
        dataStore.edit { preferences ->
            if (account == null) {
                preferences.remove(GOOGLE_DRIVE_EMAIL)
                preferences.remove(GOOGLE_DRIVE_DISPLAY_NAME)
            } else {
                preferences[GOOGLE_DRIVE_EMAIL] = account.email
                val name = account.displayName
                if (name == null) preferences.remove(GOOGLE_DRIVE_DISPLAY_NAME)
                else preferences[GOOGLE_DRIVE_DISPLAY_NAME] = name
            }
        }
    }

    // 🟢 首頁排序
    override val homeSortFlow: Flow<HomeSort> = dataStore.data.map { preferences ->
        val orderStr = preferences[HOME_SORT_ORDER] ?: HomeSortOrder.DEFAULT.name
        val order = try {
            HomeSortOrder.valueOf(orderStr)
        } catch (e: Exception) {
            HomeSortOrder.DEFAULT
        }
        HomeSort(order, preferences[HOME_SORT_DESCENDING] ?: true)
    }

    override suspend fun setHomeSort(sort: HomeSort) {
        dataStore.edit { preferences ->
            preferences[HOME_SORT_ORDER] = sort.order.name
            preferences[HOME_SORT_DESCENDING] = sort.descending
        }
    }
}
