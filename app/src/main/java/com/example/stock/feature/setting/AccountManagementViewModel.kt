package com.example.stock.feature.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = transactionRepository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentAccountId: StateFlow<Long> = settingsRepository.currentAccountIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1L
        )

    fun addAccount(name: String, currency: String, initialBalance: Double, isCashManagementEnabled: Boolean) {
        viewModelScope.launch {
            transactionRepository.upsertAccount(
                Account(
                    name = name,
                    currency = currency,
                    initialBalance = initialBalance,
                    isCashManagementEnabled = isCashManagementEnabled
                )
            )
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            transactionRepository.upsertAccount(account)
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            // 在刪除帳戶前，你可以根據需求決定是否要先檢查該帳戶下是否有交易
            // 目前 Room 設定可能是 Cascade Delete
            transactionRepository.deleteAccount(account)
        }
    }

    fun selectAccount(accountId: Long) {
        viewModelScope.launch {
            settingsRepository.setCurrentAccountId(accountId)
        }
    }
}
