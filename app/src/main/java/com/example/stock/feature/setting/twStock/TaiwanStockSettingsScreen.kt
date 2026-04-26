package com.example.stock.feature.setting.twStock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.core.ui.theme.SettingHeader
import com.example.stock.feature.setting.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaiwanStockSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val focusManager = LocalFocusManager.current

    val performSave = {
        viewModel.saveSettings()
    }

    val saveAndExit = {
        viewModel.saveSettings(onComplete = onBack)
    }

    BackHandler {
        saveAndExit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("台股交易設定") },
                navigationIcon = {
                    IconButton(onClick = saveAndExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingHeader("損益顯示設定")

            ListItem(
                headlineContent = { Text("預扣賣出成本") },
                supportingContent = { Text("在清單中之總市值及損益將自動扣除估計之賣出手續費與稅金") },
                trailingContent = {
                    Switch(
                        checked = viewModel.showPreDeduct,
                        onCheckedChange = {
                            viewModel.togglePreDeduct(it)
                        }
                    )
                }
            )

            SettingHeader("手續費設定")

            // 1. 手續費率
            OutlinedTextField(
                value = viewModel.feeRate,
                onValueChange = {
                    viewModel.feeRate = it
                    viewModel.autoSaveWithDelay()
                },
                label = { Text("預設手續費率 (%)") },
                placeholder = { Text("例如：0.1425") },
                suffix = { Text("%") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        performSave()
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                )
            )

            // 2. 券商折數
            OutlinedTextField(
                value = viewModel.discount,
                onValueChange = {
                    if (it.all { char -> char.isDigit() || char == '.' }) {
                        viewModel.discount = it
                        viewModel.autoSaveWithDelay()
                    }
                },
                label = { Text("券商折數") },
                placeholder = { Text("例如：2.8") },
                supportingText = { Text("請輸入折數，如 2.8 折或 6 折") },
                suffix = { Text("折") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        performSave()
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                )
            )

            // 3. 最低手續費
            OutlinedTextField(
                value = viewModel.minFee,
                onValueChange = {
                    viewModel.minFee = it
                    viewModel.autoSaveWithDelay()
                },
                label = { Text("最低手續費 (低收)") },
                placeholder = { Text("例如：20") },
                suffix = { Text("元") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        saveAndExit()
                    }
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // 儲存按鈕
            Text(
                text = "設定將於變更或離開時自動儲存",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}