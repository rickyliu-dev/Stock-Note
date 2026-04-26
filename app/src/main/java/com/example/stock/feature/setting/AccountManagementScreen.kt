package com.example.stock.feature.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.core.data.model.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    onBack: () -> Unit,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val currentAccountId by viewModel.currentAccountId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帳戶管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增帳戶")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(accounts) { account ->
                AccountItem(
                    account = account,
                    isSelected = account.id == currentAccountId,
                    onSelect = { viewModel.selectAccount(account.id) },
                    onEdit = { accountToEdit = account },
                    onDelete = { 
                        // 先開啟確認對話框
                        accountToDelete = account 
                    }
                )
            }
        }
    }

    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("確認刪除帳戶") },
            text = { Text("確定要刪除「${account.name}」嗎？這將會連同該帳戶下的所有交易紀錄一併刪除，且無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(account)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確定刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddDialog) {
        AccountEditDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, currency, initialBalance, isCashManagementEnabled ->
                viewModel.addAccount(name, currency, initialBalance, isCashManagementEnabled)
                showAddDialog = false
            }
        )
    }

    accountToEdit?.let { account ->
        AccountEditDialog(
            account = account,
            onDismiss = { accountToEdit = null },
            onConfirm = { name, currency, initialBalance, isCashManagementEnabled ->
                viewModel.updateAccount(account.copy(
                    name = name, 
                    currency = currency, 
                    initialBalance = initialBalance,
                    isCashManagementEnabled = isCashManagementEnabled
                ))
                accountToEdit = null
            }
        )
    }
}

@Composable
fun AccountItem(
    account: Account,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onSelect() },
        headlineContent = { Text(account.name) },
        supportingContent = { Text("${account.currency} - 初始餘額: ${account.initialBalance}") },
        leadingContent = {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "目前選中", tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "編輯")
                }
                if (account.id != 1L) { // 不允許刪除預設帳戶
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除", tint = Color.Red)
                    }
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable
fun AccountEditDialog(
    account: Account? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var currency by remember { mutableStateOf(account?.currency ?: "TWD") }
    var initialBalance by remember { mutableStateOf(account?.initialBalance?.toString() ?: "0.0") }
    var isCashManagementEnabled by remember { mutableStateOf(account?.isCashManagementEnabled ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新增帳戶" else "編輯帳戶") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("帳戶名稱") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it },
                    label = { Text("幣別 (TWD, USD...)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    label = { Text("初始餘額") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isCashManagementEnabled,
                        onCheckedChange = { isCashManagementEnabled = it }
                    )
                    Text("啟用現金管理", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, currency, initialBalance.toDoubleOrNull() ?: 0.0, isCashManagementEnabled) }) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
