package com.example.stock.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.core.ui.component.item.StockRowItem
import com.example.stock.core.ui.component.modifier.secretClick
import com.example.stock.feature.home.component.DashboardHeader
import com.example.stock.feature.home.component.dialog.UnlockPremiumDialog
import kotlinx.coroutines.flow.collectLatest

import com.example.stock.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StockViewModel = hiltViewModel(),
    mainViewModel: MainViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // 從 VM 觀察資料
    val transactions by viewModel.transactions.collectAsState()
    val assetDistribution by viewModel.assetDistribution.collectAsState()
    val assetHistory by viewModel.assetHistory.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val activeList by viewModel.activeStockList.collectAsState()
    val closedList by viewModel.closedStockList.collectAsState()
    val isCashEnabled by viewModel.isCashManagementEnabled.collectAsState()

    // 多選模式
    val isSelectionMode = viewModel.isSelectionMode
    val selectedSymbols = viewModel.selectedSymbols
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var targetAccountForMove by remember { mutableStateOf<com.example.stock.core.data.model.Account?>(null) }

    // 2. 🟢 從 MainViewModel 觀察現在選中哪個分頁 (0 = 持有中, 1 = 已清倉)
    val selectedTabIndex by mainViewModel.selectedTabIndex.collectAsState()
    val lastUpdateTimestamp by viewModel.lastUpdateTimestamp.collectAsState()

    // 格式化最後更新時間
    val lastUpdateText = remember(lastUpdateTimestamp) {
        if (lastUpdateTimestamp == 0L) "尚未更新"
        else {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            "最後更新: ${sdf.format(java.util.Date(lastUpdateTimestamp))}"
        }
    }

    // 決定現在要顯示哪個清單
    val currentList = if (selectedTabIndex == 0) activeList else closedList

    // 刪除整支股票的狀態
    var stockToDelete by remember { mutableStateOf<String?>(null) }

    val snackBarHostState = remember { SnackbarHostState() }

    var showUnlockDialog by remember { mutableStateOf(false) }

    // 初始化 Toast 監聽
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { message ->
            snackBarHostState.currentSnackbarData?.dismiss()
            snackBarHostState.showSnackbar(
                message = message,
                actionLabel = "確定",
                duration = SnackbarDuration.Short
            )
        }
    }

    val summary by viewModel.portfolioSummary.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // 帳戶相關
    val allAccounts by viewModel.allAccounts.collectAsState()
    val currentAccountId by viewModel.currentAccountId.collectAsState()
    var showAccountMenu by remember { mutableStateOf(false) }
    val currentAccount = allAccounts.find { it.id == currentAccountId }
    val currentAccountName = currentAccount?.name ?: "預設帳戶"

    val displayProfit = if (viewModel.isCumulative) {
        summary.cumulativeProfit
    } else {
        summary.unrealizedProfit
    }

    val includeDividends by viewModel.includeDividends.collectAsState()

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackBarHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("已選取 ${selectedSymbols.size} 支股票") },
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
                            Icon(Icons.Default.Delete, contentDescription = "刪除股票", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable { showAccountMenu = true }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    null,
                                    tint = Color.Red,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column {
                                    Text(
                                        "股記 StockNote",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.secretClick(
                                            times = 7,
                                            durationMillis = 3000L
                                        ) {
                                            showUnlockDialog = true
                                        }
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            currentAccountName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                allAccounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text(account.name) },
                                        onClick = {
                                            viewModel.setCurrentAccount(account.id)
                                            showAccountMenu = false
                                        },
                                        trailingIcon = {
                                            if (account.id == currentAccountId) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.updateAllPrices() },
                            enabled = !isUpdating
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "Update")
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "設定"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAdd() },
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, contentDescription = "新增交易") }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFFF5F5F5))
        ) {
            item {
                DashboardHeader(
                    totalAssets = summary.totalAssets,
                    cashBalance = summary.cashBalance,
                    totalProfit = displayProfit,
                    dailyProfit = summary.dailyProfit,
                    isCumulative = viewModel.isCumulative,
                    onToggleMode = { viewModel.isCumulative = !viewModel.isCumulative },
                    includeDividends = includeDividends,
                    onToggleDividends = { isChecked ->
                        viewModel.toggleIncludeDividends(isChecked)
                    },
                    assetDistribution = assetDistribution,
                    assetHistory = assetHistory,
                    isCashEnabled = isCashEnabled,
                    onTimeRangeSelected = { range ->
                        // 未來實作
                    }
                )
            }

            // 🔍 搜尋欄位
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF3F4F6),
                            unfocusedContainerColor = Color(0xFFF3F4F6),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // 🟢 3. 加入分頁標籤列 (TabRow)
            item {
                Column(modifier = Modifier.background(Color.White)) {
                    if (lastUpdateTimestamp > 0) {
                        Text(
                            text = lastUpdateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            textAlign = TextAlign.End
                        )
                    }
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.White,
                        contentColor = Color(0xFFEF4444),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = Color(0xFFEF4444) // 紅色底線
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { mainViewModel.selectTab(0) },
                            text = { Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.portfolio_active, activeList.size), fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { mainViewModel.selectTab(1) },
                            text = { Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.portfolio_closed, closedList.size), fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }

            // 🟢 4. 標題列 (根據 Tab 動態改變)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F8F8))
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("股票/股數", modifier = Modifier.weight(1.2f), fontSize = 14.sp, color = Color.Gray)

                    if (selectedTabIndex == 0) {
                        Text("股價", modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.End)
                        Text("均價", modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.End)
                        Text("未實現損益", modifier = Modifier.weight(1.1f), fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.End)
                    } else {
                        Text("現價", modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.End)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("總已實現損益", modifier = Modifier.weight(1.1f), fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.End)
                    }
                }
            }

            if (currentList.isEmpty() && selectedTabIndex == 1) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "目前沒有已清倉的股票",
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(currentList, key = { it.symbol }) { item ->
                    val isSelected = selectedSymbols.contains(item.symbol)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSelectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleSelection(item.symbol) },
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }

                        StockRowItem(
                            name = item.name,
                            shares = item.shares,
                            currentPrice = item.currentPrice,
                            change = item.change,
                            changePercent = item.changePercent,
                            avgCost = item.avgCost,
                            profit = item.profit,
                            profitPercent = item.profitPercent,
                            subtitle = item.subtitle,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(item.symbol)
                                } else {
                                    onNavigateToDetail(item.symbol)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.enterSelectionMode(item.symbol)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.delete_confirm_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.delete_confirm_msg, selectedSymbols.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedStocks()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.stock.R.string.cancel))
                }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
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
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    targetAccountForMove?.let { account ->
        AlertDialog(
            onDismissRequest = { targetAccountForMove = null },
            title = { Text("確認移動股票") },
            text = { Text("確定要將選中的 ${selectedSymbols.size} 支股票移動到「${account.name}」嗎？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveSelectedStocks(account.id)
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

    stockToDelete?.let { symbol ->
        AlertDialog(
            onDismissRequest = { stockToDelete = null },
            title = { Text("刪除股票") },
            text = { Text("確定要刪除 $symbol 的所有紀錄嗎？") },
            confirmButton = {
                Button(onClick = {
                    // 請注意：這裡建議在 ViewModel 新增 deleteStock(symbol) 方法
                    // 暫時用舊邏輯
                    val toRemove = transactions.filter { it.symbol == symbol }
                    toRemove.forEach { viewModel.deleteTransaction(it) }
                    stockToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { stockToDelete = null }) { Text("取消") } }
        )
    }

    if (showUnlockDialog) {
        UnlockPremiumDialog(
            onDismiss = { showUnlockDialog = false },
            onVerify = { code ->
                viewModel.verifyAndUnlock(code)
            }
        )
    }
}