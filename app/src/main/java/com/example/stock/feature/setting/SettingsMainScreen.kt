package com.example.stock.feature.setting

import android.content.ClipData
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.BuildConfig
import com.example.stock.core.data.LicenseManager
import com.example.stock.core.ui.theme.SettingHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isPremium by viewModel.isPremiumUnlocked.collectAsState()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- 匯入 CSV 的 Launcher ---
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.prepareCsvImport(it, context)
        }
    }

    // --- CSV 欄位對應對話框 ---
    viewModel.csvHeader?.let { header ->
        CsvImportDialog(
            header = header,
            onDismiss = { viewModel.clearCsvImport() },
            onConfirm = { mapping, customValues ->
                viewModel.executeCsvImport(context, mapping, customValues) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (success) {
                        onBack() // 匯入成功後返回上一頁 (首頁)
                    }
                }
            }
        )
    }

    // --- 匯出 CSV 的 Launcher ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val transactions = viewModel.getAllTransactionsForExport()
                val csvString = com.example.stock.core.util.CsvManager.exportToCsv(transactions)
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(csvString.toByteArray())
                }
                Toast.makeText(context, "匯出成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val settingCategories = remember(isPremium) {
        if (isPremium) {
            listOf(
                SettingCategory.TaiwanStock,
                SettingCategory.UsStock,
                SettingCategory.Crypto
            )
        } else {
            listOf(
                SettingCategory.TaiwanStock
            )
        }
    }

    var expandedCategoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isBackupLoading by viewModel.isBackupLoading.collectAsState()

    // --- Google Drive 備份的 Launcher ---
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                // 登入成功後再確認要執行備份還是還原，這裡示範備份
                viewModel.backupToDrive(account) { success ->
                    val msg = if (success) "備份成功！" else "備份失敗"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google 登入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val googleRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                viewModel.restoreFromDrive(account) { success ->
                    val msg = if (success) "還原成功，請重新啟動 App" else "還原失敗或找不到備份檔"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google 登入失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
        ) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
        ) {
            item {
                SettingHeader(
                    text = "交易市場設定",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            itemsIndexed(settingCategories) { index, category ->
                val isExpanded = expandedCategoryTitle == category.title
                // 箭頭旋轉動畫
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "arrow"
                )

                Column {
                    // 1. 主分類 ListItem
                    ListItem(
                        headlineContent = {
                            Text(
                                category.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        leadingContent = {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        // 箭頭改為向下，並綁定旋轉動畫
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "展開",
                                modifier = Modifier.rotate(rotation)
                            )
                        },
                        modifier = Modifier.clickable {
                            if (isExpanded) {
                                expandedCategoryTitle = null // 收合
                            } else {
                                expandedCategoryTitle = category.title // 展開
                                // 自動滑動到該項目 (index + 1 是因為前面有一個 Header item)
                                coroutineScope.launch {
                                    delay(100)
                                    listState.animateScrollToItem(index + 1)
                                }
                            }
                        }
                    )

                    // 2. 展開的子選項區域
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC)) // 給子選單一點點淺灰色背景做區隔
                        ) {
                            category.subItems.forEachIndexed { subIndex, subItem ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            subItem.title,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(start = 56.dp) // 讓文字往右縮進，對齊上面的標題
                                        .clickable {
                                            // 🟢 這裡才是真正執行導航跳轉的地方！
                                            onNavigate(subItem.route)
                                        },
                                    // 稍微修改背景色融入底色
                                    colors = androidx.compose.material3.ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    )
                                )
                                // 畫子項目的分隔線 (最後一個不畫)
                                if (subIndex < category.subItems.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        thickness = 0.5.dp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingHeader(
                    text = "雲端備份與同步",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Google Drive 雲端同步") },
                    supportingContent = { Text("備份您的交易紀錄至 Google 雲端硬碟") },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.CloudSync, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    modifier = Modifier.clickable { onNavigate(com.example.stock.navigation.AllScreens.CloudBackup.route) }
                )
            }

            item {
                SettingHeader(
                    text = "帳戶管理",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("管理多個帳戶") },
                    supportingContent = { Text("新增、修改或切換您的資產帳戶") },
                    leadingContent = { Icon(Icons.Default.Wallet, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigate(com.example.stock.navigation.AllScreens.AccountManagement.route) }
                )
            }

            if (BuildConfig.DEBUG) {
                item {
                    SettingHeader(
                        text = "資料管理 (Debug)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Button(
                            onClick = { importLauncher.launch("text/*") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("📥 匯入資料 (CSV)")
                        }

                        Button(
                            onClick = {
                                val fileName = "Stock_Backup_${System.currentTimeMillis()}.csv"
                                exportLauncher.launch(fileName)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("📤 匯出資料 (CSV)")
                        }
                    }
                }
            }

            item {
                if (BuildConfig.DEBUG) {
                    Button(
                        onClick = {
                            val newKey = LicenseManager.generateRandomKey()
                            scope.launch {
                                val clipData = ClipData.newPlainText("VIP_KEY", newKey)
                                clipboard.setClipEntry(clipData.toClipEntry())
                            }
                            Toast.makeText(context, "已複製序號：$newKey", Toast.LENGTH_SHORT).show()
                            println("新產生的序號: $newKey")
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text("🛠️ (開發者專用) 產生一組新序號")
                    }

                    // --- 👇 這是新增的「一鍵備份」按鈕 👇 ---
                    Button(
                        onClick = {
                            Toast.makeText(context, "⏳ 讀取真實資料並同步至雲端...", Toast.LENGTH_SHORT).show()

                            // 呼叫 ViewModel 的真實備份功能
                            viewModel.backupDataToCloud { isSuccess ->
                                // 這個區塊會在備份結束後執行
                                if (isSuccess) {
                                    Toast.makeText(context, "🎉 真實資料備份大成功！", Toast.LENGTH_SHORT).show()
                                    println("✅ 雲端備份成功")
                                } else {
                                    Toast.makeText(context, "😢 備份失敗，請檢查網路狀態", Toast.LENGTH_SHORT).show()
                                    println("❌ 雲端備份失敗")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("☁️ (開發者專用) 真實資料一鍵備份")
                    }
                }
            }
        }
    }
}