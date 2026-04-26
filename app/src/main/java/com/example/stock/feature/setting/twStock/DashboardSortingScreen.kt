package com.example.stock.feature.setting.twStock

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stock.feature.setting.SettingsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardSortingScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsList by viewModel.dashboardSettings.collectAsState()

    // 🟢 1. 建立原生的清單狀態
    val lazyListState = rememberLazyListState()

    // 🟢 2. 建立拖拉狀態，並把原生清單狀態傳給它
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 當發生拖拉移動時，呼叫 ViewModel 進行資料交換
        viewModel.swapDashboardItems(from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自訂儀表板版面") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF1F5F9)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "按住右側圖示即可拖曳排序",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            ) {
                // 🟢 3. LazyColumn 改吃原生狀態 lazyListState
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(settingsList, key = { it.id }) { item ->

                        // 🟢 4. 使用新的 ReorderableItem 包裝
                        ReorderableItem(reorderState, key = item.id) { isDragging ->

                            // 加上平滑的陰影動畫，讓拖拉時有「浮起來」的高級感
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "drag_shadow"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(elevation) // 陰影
                                    .background(Color.White) // 確保底色是白色的，避免重疊透明
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 左側：開關與名稱
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = item.isVisible,
                                        onCheckedChange = { viewModel.toggleDashboardItemVisibility(item.id) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (item.isVisible) Color.Black else Color.Gray
                                    )
                                }

                                // 🟢 右側：拖曳手柄 (直接在 Icon 上綁定 draggableHandle)
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "按住拖曳",
                                    tint = Color.Gray,
                                    // 魔法就在這行！只要摸到這個 Icon 就可以直接拖拉
                                    modifier = Modifier
                                        .padding(8.dp) // 增加點擊範圍
                                        .draggableHandle()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}