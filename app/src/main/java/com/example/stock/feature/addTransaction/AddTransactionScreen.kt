package com.example.stock.feature.addTransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.ui.component.dialog.DeleteTransactionDialog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun Modifier.clearErrorOnFocus(
        isError: Boolean,
        onClear: () -> Unit
    ): Modifier = this.onFocusChanged { focusState ->
        if (focusState.isFocused && isError) {
            onClear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.transactionId == -1L) "新增交易" else "編輯交易") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) }
                },
                actions = {
                    if (viewModel.transactionId != -1L) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "刪除此筆交易",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    TextButton(onClick = { viewModel.saveTransaction(onSaveSuccess) }) {
                        Text("確定", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 買入/賣出 切換
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    TransactionType.entries.filter { item ->
                        if (state.symbol == "CASH") {
                            item == TransactionType.DEPOSIT || item == TransactionType.WITHDRAW || item == TransactionType.ADJUSTMENT
                        } else {
                            val isCashType = item == TransactionType.DEPOSIT || item == TransactionType.WITHDRAW || item == TransactionType.ADJUSTMENT
                            if (!state.isCashManagementEnabled && isCashType) {
                                false
                            } else {
                                item != TransactionType.DEPOSIT && item != TransactionType.WITHDRAW && item != TransactionType.ADJUSTMENT
                            }
                        }
                    }.forEach { item ->
                        FilterChip(
                            selected = state.type == item,
                            onClick = { viewModel.onTypeChange(item) },
                            label = {
                                Text(
                                    text = item.label,
                                    color = if (state.type == item) item.getColor() else Color.Unspecified
                                )
                            },
                            leadingIcon = {
                                if (state.type == item) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = item.getColor()
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = item.getColor().copy(alpha = 0.1f),
                                selectedLabelColor = item.getColor(),
                                selectedLeadingIconColor = item.getColor()
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // --- 日期輸入區塊 ---
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.yearStr,
                        onValueChange = { viewModel.onYearChange(it) },
                        label = { Text("年") },
                        isError = state.dateError,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                    )

                    OutlinedTextField(
                        value = state.monthStr,
                        onValueChange = { viewModel.onMonthChange(it) },
                        label = { Text("月") },
                        isError = state.dateError,
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                    )

                    OutlinedTextField(
                        value = state.dayStr,
                        onValueChange = { viewModel.onDayChange(it) },
                        label = { Text("日") },
                        isError = state.dateError,
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                    )

                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "開啟日曆",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (state.dateError) {
                    Text(
                        text = "請輸入有效的真實日期",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                if (showDatePicker) {
                    val initialDateMillis = remember(state.yearStr, state.monthStr, state.dayStr) {
                        try {
                            val y = state.yearStr.toInt()
                            val m = state.monthStr.toInt()
                            val d = state.dayStr.toInt()
                            LocalDate(y, m, d)
                                .atStartOfDayIn(TimeZone.UTC)
                                .toEpochMilliseconds()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    }

                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = initialDateMillis
                    )

                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    viewModel.updateDateFromMillis(millis)
                                }
                                showDatePicker = false
                            }) { Text("確定") }
                        }
                    ) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 股票代號
                if (state.type != TransactionType.DEPOSIT && state.type != TransactionType.WITHDRAW) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearErrorOnFocus(state.symbolError) { viewModel.clearSymbolError() }) {
                        OutlinedTextField(
                            value = state.displaySymbol,
                            onValueChange = {
                                if (!state.isSymbolReadOnly) {
                                    viewModel.onSymbolChange(it)
                                }
                            },
                            enabled = !state.isSymbolReadOnly,
                            label = {
                                val labelText = when {
                                    state.symbolError -> "請輸入代號或搜尋股票"
                                    state.name.isNotEmpty() -> "已選：${state.name}"
                                    else -> "輸入代號或名稱"
                                }
                                Text(labelText)
                            },
                            isError = state.symbolError && !state.isSymbolReadOnly,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (state.searchResults.isNotEmpty()) ImeAction.Search else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (viewModel.selectFirstStock()) {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }
                                },
                                onNext = {
                                    viewModel.selectFirstStock()
                                    focusManager.moveFocus(FocusDirection.Next)
                                }
                            )
                        )

                        if (state.searchResults.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp),
                                shape = RoundedCornerShape(8.dp),
                                tonalElevation = 8.dp,
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                LazyColumn {
                                    items(state.searchResults) { (resSymbol, resName) ->
                                        val displaySymbol = resSymbol.substringBefore(".")
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    text = displaySymbol,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            supportingContent = { Text(resName) },
                                            modifier = Modifier.clickable {
                                                viewModel.onResultSelected(resSymbol, displaySymbol, resName)
                                            }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.type != TransactionType.DEPOSIT && state.type != TransactionType.WITHDRAW) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.priceStr,
                            onValueChange = { viewModel.onPriceChange(it) },
                            label = {
                                Text(
                                    when {
                                        state.priceError -> "請輸入價格"
                                        state.type == TransactionType.DIVIDEND -> "單股股息 (選填)"
                                        state.type == TransactionType.STOCK_DIVIDEND -> "單股股利 (選填) "
                                        state.type == TransactionType.SPLIT -> "新股比例 (例如 1.5)"
                                        state.type == TransactionType.CAPITAL_REDUCTION -> "退還股款 (單股)"
                                        else -> "成交單價"
                                    }
                                )
                            },
                            isError = state.priceError,
                            modifier = Modifier.weight(1f)
                                .clearErrorOnFocus(state.priceError) { viewModel.clearPriceError() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                        )

                        OutlinedTextField(
                            value = state.sharesStr,
                            onValueChange = { viewModel.onSharesChange(it) },
                            label = {
                                Text(
                                    when {
                                        state.sharesError -> "請輸入股數"
                                        state.type == TransactionType.DIVIDEND -> "除息股數 (選填)"
                                        state.type == TransactionType.STOCK_DIVIDEND -> "除權股數 (選填)"
                                        state.type == TransactionType.SPLIT -> "分割前股數"
                                        state.type == TransactionType.CAPITAL_REDUCTION -> "減資前股數"
                                        else -> "成交股數"
                                    }
                                )
                            },
                            isError = state.sharesError,
                            modifier = Modifier.weight(1f)
                                .clearErrorOnFocus(state.sharesError) { viewModel.clearSharesError() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.type != TransactionType.DEPOSIT && state.type != TransactionType.WITHDRAW) {
                        OutlinedTextField(
                            value = state.feeStr,
                            onValueChange = { viewModel.onFeeChanged(it) },
                            label = {
                                Text(
                                    when {
                                        state.feeError -> "請輸入金額"
                                        state.type == TransactionType.DIVIDEND -> "匯費 (選填)"
                                        state.type == TransactionType.CAPITAL_REDUCTION -> "手續費"
                                        else -> "手續費"
                                    }
                                )
                            },
                            isError = state.feeError,
                            modifier = Modifier.weight(1f)
                                .clearErrorOnFocus(state.feeError) { viewModel.clearFeeError() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                        )
                    }

                    OutlinedTextField(
                        value = state.totalStr,
                        onValueChange = { viewModel.onTotalChange(it) },
                        label = {
                            Text(
                                when {
                                    state.totalError -> if (state.type == TransactionType.STOCK_DIVIDEND || state.type == TransactionType.SPLIT) "請輸入總股數" else "請輸入總金額"
                                    state.type == TransactionType.DIVIDEND -> "股息總額"
                                    state.type == TransactionType.STOCK_DIVIDEND -> "總股數"
                                    state.type == TransactionType.SPLIT -> "分割後總股數"
                                    state.type == TransactionType.CAPITAL_REDUCTION -> "減資後總股數"
                                    state.type == TransactionType.DEPOSIT || state.type == TransactionType.WITHDRAW -> "金額"
                                    else -> "總金額"
                                }
                            )
                        },
                        isError = state.totalError,
                        modifier = Modifier.weight(1f)
                            .clearErrorOnFocus(state.totalError) { viewModel.clearTotalError() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.saveTransaction(onSaveSuccess)
                                focusManager.clearFocus()
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.onNoteChange(it) },
                    label = { Text("備註") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.saveTransaction(onSaveSuccess)
                        focusManager.clearFocus()
                    })
                )
            }
            if (showDeleteDialog) {
                DeleteTransactionDialog(
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        viewModel.deleteTransaction(onSuccess = onBack)
                        showDeleteDialog = false
                    }
                )
            }
        }
    }
}
