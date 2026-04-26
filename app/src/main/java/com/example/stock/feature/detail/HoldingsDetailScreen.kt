package com.example.stock.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.feature.detail.component.MainProfitCard
import com.example.stock.feature.detail.component.SummaryItemCard
import com.example.stock.feature.detail.component.TransactionItemCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HoldingsDetailScreen(
    onBack: () -> Unit,
    onAddTransaction: (String) -> Unit,
    onEditTransaction: (String, Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val symbol = viewModel.symbol
    // 去除代號後綴，用於畫面顯示用
    val displaySymbol = symbol.substringBefore(".")
    val name by viewModel.stockName.collectAsState()
    val transactions by viewModel.relatedTransactions.collectAsState()
    val displayItems by viewModel.dashboardDisplayItems.collectAsState()
    val cumulativeProfitData by viewModel.cumulativeProfit.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val currentAccountId by viewModel.currentAccountId.collectAsState()

    var showMoveDialog by remember { mutableStateOf(false) }
    var targetAccountForMove by remember { mutableStateOf<com.example.stock.core.data.model.Account?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 攔截系統返回鍵
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            if (viewModel.isSelectionMode) {
                // 選取模式
                TopAppBar(
                    title = { Text("已選取 ${viewModel.selectedIds.size} 筆") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "移動至其他帳戶")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // 一般模式
                TopAppBar(
                    title = {
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            if (symbol != "CASH") {
                                Text(
                                    text = displaySymbol,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onAddTransaction(symbol) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        containerColor = Color(0xFFF0F4F8)
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MainProfitCard(
                    value = cumulativeProfitData.amountText,
                    valueColor = cumulativeProfitData.color,
                    percentage = cumulativeProfitData.percentageText
                )
            }

            // 各項資料
            item {
                if (displayItems.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 3,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayItems.forEach { (item, valueText, valueColor) ->
                            SummaryItemCard(
                                title = item.title,
                                value = valueText,
                                valueColor = valueColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 提示文字
            item {
                Text(
                    text = "長按單筆紀錄可刪除",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            val sortedList = transactions.sortedByDescending { it.date }

            items(sortedList, key = { it.id }) { trans ->
                val isSelected = viewModel.selectedIds.contains(trans.id)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Checkbox
                    AnimatedVisibility(visible = viewModel.isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleSelection(trans.id) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    TransactionItemCard(
                        transaction = trans,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (viewModel.isSelectionMode) {
                                // 選取模式
                                viewModel.toggleSelection(trans.id)
                            } else {
                                // 一般模式
                                onEditTransaction(trans.symbol, trans.id)
                            }
                        },
                        onLongClick = {
                            if (!viewModel.isSelectionMode) {
                                viewModel.enterSelectionMode(trans.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("取消")
                }
            },
            title = { Text("選擇目標帳戶") },
            text = {
                Column {
                    allAccounts.forEach { account ->
                        val isCurrent = account.id == currentAccountId
                        ListItem(
                            modifier = Modifier.clickable(enabled = !isCurrent) {
                                targetAccountForMove = account
                                showMoveDialog = false
                            },
                            headlineContent = { 
                                Text(
                                    text = account.name + (if (isCurrent) " (當前帳戶)" else ""),
                                    color = if (isCurrent) Color.Gray else Color.Unspecified
                                ) 
                            },
                            supportingContent = { 
                                Text(
                                    text = account.currency,
                                    color = if (isCurrent) Color.Gray else Color.Unspecified
                                ) 
                            },
                            trailingContent = {
                                if (isCurrent) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        )
                    }
                }
            }
        )
    }

    targetAccountForMove?.let { account ->
        AlertDialog(
            onDismissRequest = { targetAccountForMove = null },
            title = { Text("確認移動交易") },
            text = { Text("確定要將選中的 ${viewModel.selectedIds.size} 筆交易移動到「${account.name}」嗎？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveSelectedTransactions(account.id)
                    targetAccountForMove = null
                }) {
                    Text("確定移動")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetAccountForMove = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("確認刪除交易") },
            text = { Text("確定要刪除選中的 ${viewModel.selectedIds.size} 筆交易紀錄嗎？此動作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedTransactions()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確定刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
