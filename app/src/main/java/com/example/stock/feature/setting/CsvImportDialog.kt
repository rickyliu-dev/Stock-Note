package com.example.stock.feature.setting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportDialog(
    header: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>, Map<String, String>) -> Unit
) {
    // App 內部的欄位定義
    val appFields = listOf(
        "symbol" to "股票代號 *",
        "name" to "名稱",
        "type" to "交易類型",
        "date" to "日期 (yyyy-MM-dd)",
        "price" to "成交價",
        "shares" to "股數",
        "fee" to "手續費",
        "dividend" to "股息",
        "total" to "總金額",
        "note" to "備註",
        "accountId" to "帳戶 ID",
        "multiplier" to "乘數",
        "participatingShares" to "除權息股數"
    )

    // 儲存欄位對應狀態：Map<App欄位名, CSV欄位索引>
    val mapping = remember { 
        mutableStateMapOf<String, Int>().apply {
            // 嘗試自動匹配 (依據名稱相似度)
            appFields.forEach { (field, _) ->
                val index = header.indexOfFirst { h -> h.contains(field, ignoreCase = true) }
                if (index != -1) put(field, index)
            }
        }
    }

    // 儲存自定義值：Map<App欄位名, 自定義字串>
    val customValues = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定 CSV 欄位對應") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("請選擇對應的 CSV 標頭或輸入固定值：", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                appFields.forEach { (field, label) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var expanded by remember { mutableStateOf(false) }
                            val isCustom = customValues.containsKey(field)

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val selectedIndex = mapping[field]
                                    Text(
                                        text = when {
                                            isCustom -> "使用固定值"
                                            selectedIndex != null -> header[selectedIndex]
                                            else -> "不匯入 / 沒對應"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("不匯入 / 沒對應") },
                                        onClick = { 
                                            mapping.remove(field)
                                            customValues.remove(field)
                                            expanded = false 
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("自定義欄位名稱") },
                                        onClick = { 
                                            mapping.remove(field)
                                            if (!customValues.containsKey(field)) {
                                                customValues[field] = ""
                                            }
                                            expanded = false 
                                        }
                                    )
                                    HorizontalDivider()
                                    header.forEachIndexed { index, h ->
                                        DropdownMenuItem(
                                            text = { Text(h) },
                                            onClick = { 
                                                mapping[field] = index
                                                customValues.remove(field)
                                                expanded = false 
                                            }
                                        )
                                    }
                                }
                            }

                            if (isCustom) {
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = customValues[field] ?: "",
                                    onValueChange = { customValues[field] = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("輸入固定值", style = MaterialTheme.typography.bodySmall) },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mapping.toMap(), customValues.toMap()) },
                enabled = mapping.containsKey("symbol") || (customValues["symbol"]?.isNotBlank() == true)
            ) {
                Text("開始匯入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
