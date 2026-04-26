package com.example.stock.feature.setting

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.api.GoogleSheetApi
import com.example.stock.core.data.EnterConstants
import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.dataClass.StockItem
import com.example.stock.core.data.enumClass.DashboardIds
import com.example.stock.core.data.model.CostBasisMethod
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.domain.GetStockInventoryUseCase
import com.example.stock.core.util.CsvManager
import com.example.stock.core.util.GoogleDriveManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val googleDriveManager: GoogleDriveManager,
    private val getStockInventoryUseCase: GetStockInventoryUseCase
) : ViewModel() {
    private var saveJob: Job? = null

    private val api = GoogleSheetApi()

    // 畫面的狀態
    var feeRate by mutableStateOf(MarketConstants.Taiwan.DEFAULT_FEE_RATE)
    var discount by mutableStateOf(MarketConstants.Taiwan.DISCOUNT)
    var minFee by mutableStateOf(MarketConstants.Taiwan.DEFAULT_MIN_FEE)
    var showPreDeduct by mutableStateOf(false)
    var isCashManagementEnabled by mutableStateOf(false)

    private val _dashboardSettings = MutableStateFlow<List<DashboardSettingItem>>(emptyList())
    val dashboardSettings = _dashboardSettings.asStateFlow()

    val isPremiumUnlocked = repository.isPremiumUnlockedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isCashManagementEnabledFlow = repository.isCashManagementEnabledFlow

    val lastUpdateTimestampFlow = repository.lastUpdateTimestampFlow


    init {
        viewModelScope.launch {
            repository.twSettingsFlow.collect { (f, d, m, show) ->
                feeRate = f
                discount = d
                minFee = m
                showPreDeduct = show
            }
        }

        viewModelScope.launch {
            repository.isCashManagementEnabledFlow.collect { enabled ->
                isCashManagementEnabled = enabled
            }
        }

        viewModelScope.launch {
            repository.dashboardSettingsFlow.collect { savedList ->
                if (savedList != null) {
                    _dashboardSettings.value = savedList
                } else {
                    // 如果是第一次使用，給予預設值
                    _dashboardSettings.value = listOf(
                        DashboardSettingItem(DashboardIds.AVG_COST, "平均成本"),
                        DashboardSettingItem(DashboardIds.AVG_BUY, "平均買價"),
                        DashboardSettingItem(DashboardIds.SHARES, "持有股數"),
                        DashboardSettingItem(DashboardIds.MARKET_VAL, "總市值"),
                        DashboardSettingItem(DashboardIds.TOTAL_COST, "總成本"),
                        DashboardSettingItem(DashboardIds.DIVIDEND, "股息收入"),
                        DashboardSettingItem(DashboardIds.DAILY_PROFIT, "今日損益"),
                        DashboardSettingItem(DashboardIds.UNREALIZED, "未實現損益"),
                        DashboardSettingItem(DashboardIds.REALIZED, "已實現損益")
                    )
                }
            }
        }
    }

    fun autoSaveWithDelay() {
        saveJob?.cancel()

        saveJob = viewModelScope.launch {
            delay(EnterConstants.DEBOUNCE_DELAY)
            saveSettings()
        }
    }

    fun saveSettings(onComplete: (() -> Unit)? = null) {
        saveJob?.cancel()

        viewModelScope.launch {
            repository.saveTwSettings(feeRate, discount, minFee, showPreDeduct)

            onComplete?.invoke()
        }
    }

    // 按鈕使用
    fun togglePreDeduct(checked: Boolean) {
        showPreDeduct = checked
        autoSaveWithDelay()
    }

    fun toggleCashManagement(checked: Boolean) {
        isCashManagementEnabled = checked
        viewModelScope.launch {
            repository.setCashManagementEnabled(checked)
        }
    }

    // 切換顯示/隱藏
    fun toggleDashboardItemVisibility(id: DashboardIds) {
        val currentList = _dashboardSettings.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }

        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(isVisible = !item.isVisible)
            _dashboardSettings.value = currentList

            viewModelScope.launch {
                repository.saveDashboardSettings(_dashboardSettings.value)
            }
        }
    }

    fun swapDashboardItems(fromIndex: Int, toIndex: Int) {
        val currentList = _dashboardSettings.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val movedItem = currentList.removeAt(fromIndex)
            currentList.add(toIndex, movedItem)
            _dashboardSettings.value = currentList

            viewModelScope.launch {
                repository.saveDashboardSettings(_dashboardSettings.value)
            }
        }
    }

    private val _isBackupLoading = MutableStateFlow(false)
    val isBackupLoading = _isBackupLoading.asStateFlow()

    fun getGoogleSignInIntent() = googleDriveManager.getSignInIntent()

    fun signOutFromGoogle(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBackupLoading.value = true
            val success = googleDriveManager.signOut()
            _isBackupLoading.value = false
            onResult(success)
        }
    }

    fun backupToDrive(account: GoogleSignInAccount, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBackupLoading.value = true
            val success = googleDriveManager.backupDatabase(account)
            if (success) {
                repository.setLastUpdateTimestamp(System.currentTimeMillis())
            }
            _isBackupLoading.value = false
            onResult(success)
        }
    }

    fun restoreFromDrive(account: GoogleSignInAccount, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBackupLoading.value = true
            val success = googleDriveManager.restoreDatabase(account)
            _isBackupLoading.value = false
            
            if (success) {
                android.util.Log.d("SettingsViewModel", "資料庫還原成功，UI 應已自動更新")
            }

            onResult(success)
        }
    }

    // --- 新增：CSV 匯入暫存狀態 ---
    var csvHeader by mutableStateOf<List<String>?>(null)
    var pendingCsvUri by mutableStateOf<Uri?>(null)

    fun prepareCsvImport(uri: Uri, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val header = CsvManager.readHeader(inputStream)
                if (header != null) {
                    withContext(Dispatchers.Main) {
                        csvHeader = header
                        pendingCsvUri = uri
                    }
                } else {
                    // 處理讀取標頭失敗
                }
            }
        }
    }

    fun clearCsvImport() {
        csvHeader = null
        pendingCsvUri = null
    }

    fun executeCsvImport(
        context: android.content.Context,
        mapping: Map<String, Int>,
        customValues: Map<String, String>,
        onResult: (Boolean, String) -> Unit
    ) {
        val uri = pendingCsvUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val importedItems = CsvManager.parseCsv(inputStream, mapping, customValues)
                    if (importedItems.isNotEmpty()) {
                        transactionRepository.upsertTransactions(importedItems)
                        withContext(Dispatchers.Main) {
                            onResult(true, "成功匯入 ${importedItems.size} 筆資料")
                            clearCsvImport()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(false, "找不到有效的資料內容，請檢查對應欄位是否正確")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "匯入出錯：${e.localizedMessage}")
                }
            }
        }
    }

    // --- 修改原本的 importCsvData (改為由 executeCsvImport 處理) ---
    // (可以移除舊的 importCsvData)

    suspend fun getAllTransactionsForExport(): List<TransactionItem> {
        return transactionRepository.transactionsDesc.first()
    }

    fun backupDataToCloud(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBackupLoading.value = true
            try {
                // 1. 取得所有交易紀錄
                val allTransactions = transactionRepository.transactionsDesc.first()
                
                // 2. 透過 UseCase 計算庫存
                val result = getStockInventoryUseCase(
                    allTransactions = allTransactions,
                    method = CostBasisMethod.AVERAGE_COST,
                    includeDividends = true
                )

                // 3. 轉成 StockItem 格式 (只備份庫存不為 0 的)
                val stockItems = result.inventoryMap.filter { it.value.shares > 0 }.map { (symbol, inventory) ->
                    val name = allTransactions.find { it.symbol == symbol }?.name ?: symbol
                    StockItem(
                        symbol = symbol,
                        name = name,
                        cost = inventory.totalCost,
                        shares = inventory.shares
                    )
                }

                // 4. 上傳雲端
                val isSuccess = api.backupAllToCloud(stockItems)
                onResult(isSuccess)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            } finally {
                _isBackupLoading.value = false
            }
        }
    }
}
