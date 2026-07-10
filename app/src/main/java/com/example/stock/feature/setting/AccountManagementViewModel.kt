package com.example.stock.feature.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.model.Account
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
            transactionRepository.deleteAccount(account)
            
            // 如果刪除的是當前選取的帳戶，則切換到剩餘的第一個帳戶
            val currentId = settingsRepository.currentAccountIdFlow.first()
            if (account.id == currentId) {
                val remaining = transactionRepository.allAccounts.first()
                if (remaining.isNotEmpty()) {
                    settingsRepository.setCurrentAccountId(remaining.first().id)
                } else {
                    // 如果沒有帳戶了，由 Repository 初始化邏輯建立或在此建立
                    // 這裡先設回 1L，Repository.init 通常會確保 1L 存在
                    settingsRepository.setCurrentAccountId(1L)
                }
            }
        }
    }

    fun selectAccount(accountId: Long) {
        viewModelScope.launch {
            settingsRepository.setCurrentAccountId(accountId)
        }
    }
}
