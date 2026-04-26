package com.example.stock.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stock.feature.home.DashboardPage

@Composable
fun DashboardHeader(
    // Page 1: 既有資料
    totalAssets: Double,
    cashBalance: Double,
    totalProfit: Double,
    dailyProfit: Double,
    isCumulative: Boolean,
    onToggleMode: () -> Unit,
    includeDividends: Boolean,
    onToggleDividends: (Boolean) -> Unit,

    // Page 2: 圓餅圖資料 (股票代號 -> 市值)
    assetDistribution: Map<String, Double> = emptyMap(),

    // Page 3: 折線圖資料 (時間戳 -> 總資產)
    assetHistory: List<Pair<Long, Double>> = emptyList(),
    isCashEnabled: Boolean = false,
    onTimeRangeSelected: (String) -> Unit = {} // 切換時間範圍 (1W, 1M, 1Y)
) {
    val pages = remember(totalProfit, assetDistribution, assetHistory) {
        buildList {
            // 永遠顯示第一頁
            add(DashboardPage.ProfitOverview(totalProfit, isCumulative, includeDividends))

            // 有資料才顯示圓餅圖
            if (assetDistribution.isNotEmpty()) {
                add(DashboardPage.AssetPieChart(assetDistribution))
            }

            // 取消註解這裡就能動態增加第三頁
            // add(DashboardPage.AssetLineChart(assetHistory))
        }
    }
    // 總共有 3 頁
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(220.dp), // 設定固定高度，避免滑動時高度跳動
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. 滑動內容區
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f) // 佔據大部分空間
            ) { index ->
                when (val pageItem = pages[index]) {
                    is DashboardPage.ProfitOverview -> {
                        ProfitOverviewPage(
                            totalAssets = totalAssets,
                            cashBalance = cashBalance,
                            totalProfit = totalProfit,
                            dailyProfit = dailyProfit,
                            isCumulative = isCumulative,
                            onToggleMode = onToggleMode,
                            includeDividends = includeDividends,
                            onToggleDividends = onToggleDividends,
                            isCashEnabled = isCashEnabled
                        )
                    }
                    is DashboardPage.AssetPieChart -> {
                        AssetPieChartPage(data = pageItem.data)
                    }
                    is DashboardPage.AssetLineChart -> {
                        AssetLineChartPage(
                            historyData = pageItem.historyData,
                            onRangeSelected = onTimeRangeSelected
                        )
                    }
                }
            }

            // 2. 頁面指示器 (下面的小圓點)
            Row(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) Color.DarkGray else Color.LightGray
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(6.dp)
                    )
                }
            }
        }
    }
}