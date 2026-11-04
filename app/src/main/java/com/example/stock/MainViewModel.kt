package com.example.stock

import androidx.lifecycle.ViewModel
import com.example.stock.navigation.FeatureEntry
import com.example.stock.navigation.SettingsEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    featureEntries: Set<@JvmSuppressWildcards FeatureEntry>,
    settingsEntries: Set<@JvmSuppressWildcards SettingsEntry>
) : ViewModel() {
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()

    // flavor 提供的功能入口；注入後不會再變，依標題排序讓顯示順序固定
    val featureEntries: List<FeatureEntry> = featureEntries.sortedBy { it.title }

    // 只用來在 MainNavGraph 註冊路由；設定頁的顯示由 SettingsViewModel 提供
    val settingsEntries: List<SettingsEntry> = settingsEntries.sortedBy { it.title }

    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
    }
}
