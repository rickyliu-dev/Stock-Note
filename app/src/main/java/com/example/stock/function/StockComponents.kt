package com.example.stock.function

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.stock.core.data.dataClass.AllocationRule
import com.example.stock.core.data.model.Transaction
import com.example.stock.core.data.model.TransactionType
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

// --------------------
// UI Components
// --------------------

@Composable
fun DataManagementDialog(
    onDismiss: () -> Unit,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("資料備份與還原")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📂 本機檔案 (JSON)", style = MaterialTheme.typography.titleMedium, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                Text("您可以將交易紀錄匯出成 JSON 檔案自行保存，或是從先前備份的檔案還原資料。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLocalExport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("匯出檔案", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onLocalImport,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("匯入檔案", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

// 收益分配規則設定 Dialog
@Composable
fun AllocationSettingsDialog(
    rules: List<AllocationRule>,
    onSaveRules: (List<AllocationRule>) -> Unit,
    onDismiss: () -> Unit
) {
    val currentRules = remember { mutableStateListOf<AllocationRule>().apply { addAll(rules) } }
    var newName by remember { mutableStateOf("") }
    var newPercent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收益分配規則設定") },
        text = {
            Column {
                Text("設定當獲利了結時，資金的分配比例 (總和建議為 100%)。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                // 新增規則區
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("帳戶名稱") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = newPercent,
                        onValueChange = { newPercent = it },
                        label = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(60.dp),
                        singleLine = true
                    )
                    IconButton(onClick = {
                        if (newName.isNotBlank() && newPercent.toDoubleOrNull() != null) {
                            currentRules.add(
                                AllocationRule(
                                    id = System.currentTimeMillis(),
                                    name = newName,
                                    percentage = newPercent.toDouble()
                                )
                            )
                            newName = ""
                            newPercent = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()

                // 列表
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(currentRules) { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${rule.name}: ${rule.percentage}%", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { currentRules.remove(rule) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveRules(currentRules); onDismiss() }) { Text("儲存設定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// 收益分配結果檢視 Dialog
@Composable
fun AllocationResultDialog(
    totalRealizedProfit: Double,
    rules: List<AllocationRule>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("已實現損益分配")
            }
        },
        text = {
            Column {
                Text("總已實現損益：${formatMoney(totalRealizedProfit)}", fontWeight = FontWeight.Bold, color = if(totalRealizedProfit>0) Color.Red else Color.Green)
                Spacer(modifier = Modifier.height(16.dp))

                if (totalRealizedProfit <= 0) {
                    Text("目前無獲利可分配，或處於虧損狀態。", color = Color.Gray)
                } else if (rules.isEmpty()) {
                    Text("尚未設定分配規則，請至側邊欄設定。", color = Color.Gray)
                } else {
                    LazyColumn {
                        items(rules) { rule ->
                            val amount = totalRealizedProfit * (rule.percentage / 100.0)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rule.name)
                                Text(formatMoney(amount), fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (rule.percentage / 100.0).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                trackColor = Color.LightGray,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // 顯示剩餘未分配
                        val allocatedPercent = rules.sumOf { it.percentage }
                        if (allocatedPercent < 100.0) {
                            // ⚠️ 修正：這裡必須包在 item { ... } 裡面，不能直接寫在 LazyColumn 內
                            item {
                                val remaining = totalRealizedProfit * ((100.0 - allocatedPercent) / 100.0)
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("未分配/保留", color = Color.Gray)
                                        Text(formatMoney(remaining), color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}


@Composable
fun PortfolioSelectionDialog(
    portfolios: List<String>,
    currentPortfolio: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切換持股組合") },
        text = {
            LazyColumn {
                item {
                    PortfolioOptionItem(name = "全部", isSelected = currentPortfolio == "全部", onClick = { onSelect("全部") })
                }
                items(portfolios) { name ->
                    PortfolioOptionItem(name = name, isSelected = currentPortfolio == name, onClick = { onSelect(name) })
                }
                if (portfolios.isEmpty()) {
                    item { Text("目前沒有自訂組合，請在新增交易時設定。", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PortfolioOptionItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun AssetSummaryCard(
    totalAssets: Double,
    profit: Double,
    isProfit: Boolean,
    includeDividends: Boolean,
    onToggleDividends: (Boolean) -> Unit,
    includeRealized: Boolean,
    onToggleRealized: (Boolean) -> Unit,
    realizedVal: Double,
    unrealizedVal: Double,
    currentPortfolioName: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
                .padding(20.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("預估總資產 (TWD)", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium)
                    Surface(color = Color(0xFF334155), shape = RoundedCornerShape(4.dp)) {
                        Text(text = currentPortfolioName, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text("$ ${NumberFormat.getNumberInstance(Locale.US).format(totalAssets)}", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF334155))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("總損益 (${if(includeRealized) "含已實現" else "僅未實現"})", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = if (isProfit) Color(0xFFF87171) else Color(0xFF4ADE80)
                            Text("${if (isProfit) "+" else ""}${NumberFormat.getNumberInstance(Locale.US).format(profit)}", color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("含息", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
                            Switch(checked = includeDividends, onCheckedChange = onToggleDividends, modifier = Modifier.scale(0.6f).height(30.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("含已實現", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
                            Switch(checked = includeRealized, onCheckedChange = onToggleRealized, modifier = Modifier.scale(0.6f).height(30.dp))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top=8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("未實現: ${formatMoney(unrealizedVal)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("已實現: ${formatMoney(realizedVal)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HoldingsPieChart(inventory: Map<String, Pair<Int, Double>>, prices: Map<String, Double>) {
    // 這裡的 inventory.first 是股數/口數，計算市值時需要考慮 multiplier
    // 但目前簡易版先維持 (股數 * 股價)，若要精確顯示期貨總曝險需傳入 multiplier
    // 這裡我們假設傳進來的 inventory 已經處理過，或者我們只顯示 "股數/口數 * 價格" 的概念值
    val dataPoints = inventory.map { (symbol, data) ->
        val value = data.first * (prices[symbol] ?: 0.0)
        symbol to value
    }.filter { it.second > 0 }.sortedByDescending { it.second }
    val total = dataPoints.sumOf { it.second }
    if (total == 0.0) return
    val colors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
    Row(modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(120.dp)) {
                var startAngle = -90f
                dataPoints.forEachIndexed { index, point ->
                    val sweepAngle = (point.second / total * 360).toFloat()
                    drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 40f))
                    startAngle += sweepAngle
                }
            }
            Text("${dataPoints.size} 檔", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            dataPoints.take(5).forEachIndexed { index, point ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(colors[index % colors.size], CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${point.first} ${(point.second / total * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoldingItem(
    symbol: String,
    name: String,
    shares: Int,
    marketValue: Double,
    profit: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(name, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text(symbol, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$${NumberFormat.getInstance().format(marketValue.toInt())}", fontWeight = FontWeight.Bold)
                val isProfit = profit > 0
                Text(text = formatMoney(profit), color = if(isProfit) Color.Red else Color.Green, style = MaterialTheme.typography.bodySmall)
            }
            // 🆕 右邊：快速新增按鈕
            // 只有持倉大於 0 時才顯示快速加碼，或是隨時都顯示也可以
            IconButton(onClick = onQuickAdd) {
                Icon(
                    imageVector = Icons.Default.EditNote, // 或是 NoteAdd
                    contentDescription = "快速記帳",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DeleteHoldingDialog(symbol: String, name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444)) },
        title = { Text("刪除持股確認") },
        text = { Text("您確定要刪除「$name ($symbol)」嗎？\n\n這將會移除該股票的所有買入、賣出與股息紀錄，此動作無法復原。", color = Color.Gray) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Text("確認刪除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun DeleteTransactionDialog(transaction: Transaction, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444)) },
        title = { Text("刪除交易確認") },
        text = {
            val typeStr = if (transaction.type == TransactionType.BUY) "買入" else "賣出"
            Text("確定要刪除這筆「$typeStr ${transaction.shares} 股」的紀錄嗎？\n(價格: ${transaction.price})", color = Color.Gray)
        },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Text("刪除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun StockSearchSection(
    searchResults: List<Pair<String, String>>,
    isSearching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String, String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("輸入代號或名稱 (如: 2330)") },
            leadingIcon = { if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
            singleLine = true
        )
        AnimatedVisibility(visible = searchResults.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    searchResults.take(5).forEach { (code, name) ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(code, name) }.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text(code, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoldingsDetailDialog(
    symbol: String,
    name: String,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onDeleteTransaction: (Transaction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$name ($symbol) 交易明細") },
        text = {
            Column {
                Text("長按單筆紀錄可刪除", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(transactions) { t ->
                        Box(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onDeleteTransaction(t) }).padding(vertical = 8.dp)) {
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    val assetTypeStr = ""
                                    Text("${if (t.type == TransactionType.BUY) "買入" else "賣出"}$assetTypeStr", color = if (t.type == TransactionType.BUY) Color.Red else Color.Green, fontWeight = FontWeight.Bold)
                                    Text(t.date, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("${t.shares} 股 @ ${t.price}")
                                }
                                if (t.dividend > 0) { Text("股息: +${t.dividend}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B)) }
                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("關閉") } }
    )
}

// ⚠️ 重點修改：新增資產類型與乘數欄位
@Composable
fun AddTransactionDialog(
    searchStockFunction: suspend (String) -> List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.BUY) }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var dividend by remember { mutableStateOf("") }

    var searchResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isSelectedFromList by remember { mutableStateOf(false) }

    LaunchedEffect(symbol) {
        if (isSelectedFromList) { isSelectedFromList = false; return@LaunchedEffect }
        if (symbol.length >= 2) {
            isSearching = true
            delay(500)
            searchResults = searchStockFunction(symbol)
            isSearching = false
        } else { searchResults = emptyList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增交易") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. 買賣與資產類型選擇
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)).padding(4.dp)) {
                    listOf(TransactionType.BUY, TransactionType.SELL).forEach { t ->
                        val isSelected = type == t
                        Button(
                            onClick = { type = t },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color.White else Color.Transparent, contentColor = if (isSelected) (if (t == TransactionType.BUY) Color(0xFFEF4444) else Color(0xFF22C55E)) else Color(0xFF94A3B8)),
                            elevation = ButtonDefaults.buttonElevation(if (isSelected) 1.dp else 0.dp)
                        ) { Text(if (t == TransactionType.BUY) "買入" else "賣出") }
                    }
                }

                StockSearchSection(
                    searchResults = searchResults, isSearching = isSearching, query = symbol,
                    onQueryChange = { symbol = it },
                    onSelect = { s, n -> isSelectedFromList = true; symbol = s; name = n; searchResults = emptyList() }
                )

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名稱 (選填)") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("價格") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = shares, onValueChange = { shares = it }, label = { Text("股數") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }


                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (type == TransactionType.SELL) {
                        OutlinedTextField(value = dividend, onValueChange = { dividend = it }, label = { Text("股息 (選填)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (symbol.isNotBlank() && price.isNotBlank() && shares.isNotBlank()) {
                        onConfirm(
                            Transaction(
                                id = System.currentTimeMillis(),
                                symbol = symbol,
                                name = name.ifBlank { symbol },
                                price = price.toDouble(),
                                shares = shares.toInt(),
                                date = "", note = "",
                                dividend = dividend.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) { Text("確認") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun AdMobBanner() {
    AndroidView(modifier = Modifier.fillMaxWidth().wrapContentHeight(), factory = { context -> AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) } })
}

fun formatMoney(value: Double): String {
    val symbol = if (value > 0) "+" else ""
    return "$symbol${NumberFormat.getInstance().format(value.toInt())}"
}

fun Modifier.scale(scale: Float) = this.then(Modifier.graphicsLayer(scaleX = scale, scaleY = scale))