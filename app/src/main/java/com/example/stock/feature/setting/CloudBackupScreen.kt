package com.example.stock.feature.setting

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.core.data.model.AutoBackupInterval
import com.example.stock.core.util.DriveAuthResult
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isBackupLoading by viewModel.isBackupLoading.collectAsState()
    val lastUpdateTimestamp by viewModel.lastUpdateTimestampFlow.collectAsState(initial = 0L)
    val autoBackupInterval by viewModel.autoBackupInterval.collectAsState()
    
    val currentAccount by viewModel.googleDriveAccount.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 等待授權畫面回來後要執行的操作（登入／備份／還原）
    var pendingAction by rememberSaveable { mutableStateOf<GoogleDriveAction?>(null) }

    // 取得授權後執行操作並顯示結果；還原的訊息較長，顯示久一點
    val runAction: (GoogleDriveAction, String) -> Unit = { action, accessToken ->
        viewModel.runDriveAction(action, accessToken) { _, message ->
            val duration = if (action == GoogleDriveAction.RESTORE) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(context, message, duration).show()
        }
    }

    // --- Google Drive 授權畫面（選帳號／同意存取）的 Launcher ---
    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode != Activity.RESULT_OK || action == null) {
            Toast.makeText(context, "已取消 Google 授權", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        when (val auth = viewModel.handleAuthorizationResult(result.data)) {
            is DriveAuthResult.Granted -> runAction(action, auth.accessToken)
            is DriveAuthResult.NeedsResolution,
            is DriveAuthResult.Failed -> Toast.makeText(context, "Google 授權失敗", Toast.LENGTH_SHORT).show()
        }
    }

    // 先要求授權：已授權直接執行；需要使用者確認時開授權畫面，回來後再執行
    val startDriveAction: (GoogleDriveAction) -> Unit = { action ->
        viewModel.requestDriveAccess { auth ->
            when (auth) {
                is DriveAuthResult.Granted -> runAction(action, auth.accessToken)
                is DriveAuthResult.NeedsResolution -> {
                    pendingAction = action
                    authorizationLauncher.launch(
                        IntentSenderRequest.Builder(auth.pendingIntent.intentSender).build()
                    )
                }
                is DriveAuthResult.Failed ->
                    Toast.makeText(context, "Google 授權失敗: ${auth.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("雲端備份與同步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 帳號資訊卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = currentAccount?.let { it.displayName ?: it.email } ?: "尚未登入",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        Text(
                            text = currentAccount?.email ?: "點擊登入按鈕進行登入",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    
                    if (currentAccount != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { showLogoutConfirm = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("登出帳號", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { startDriveAction(GoogleDriveAction.LOGIN) },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("登入", fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 備份狀態
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (lastUpdateTimestamp > 0) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (lastUpdateTimestamp > 0) Color(0xFF10B981) else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("上次備份時間", fontSize = 14.sp, color = Color.Gray)
                        Text(
                            text = if (lastUpdateTimestamp == 0L) "尚未有備份紀錄"
                            else SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(lastUpdateTimestamp)),
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 自動備份頻率
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("自動備份", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutoBackupInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = autoBackupInterval == interval,
                                onClick = { viewModel.setAutoBackupInterval(interval) },
                                label = { Text(interval.label) },
                                // 未登入時只能關閉
                                enabled = currentAccount != null || interval == AutoBackupInterval.OFF
                            )
                        }
                    }
                    Text(
                        text = if (currentAccount == null) "請先登入 Google 帳號才能啟用自動備份"
                        else "有網路且電量充足時，會在背景自動備份，不需開啟 App",
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. 操作按鈕
            if (isBackupLoading) {
                CircularProgressIndicator()
                Text("正在處理中，請稍候...", modifier = Modifier.padding(top = 16.dp), color = Color.Gray)
            } else {
                Button(
                    onClick = { startDriveAction(GoogleDriveAction.BACKUP) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("立即備份至雲端", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { startDriveAction(GoogleDriveAction.RESTORE) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("從雲端還原舊版本", fontSize = 16.sp)
                }
                
                Text(
                    text = "提示：還原將會覆蓋目前手機上的所有交易紀錄，並自動重啟 App。",
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("確認登出") },
            text = { Text("確定要登出 Google 帳號嗎？登出後將無法進行雲端同步。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.signOutFromGoogle { success ->
                            if (success) {
                                onBack() // 登出後返回上一頁
                            }
                        }
                    }
                ) {
                    Text("確認登出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
