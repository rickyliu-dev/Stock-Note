package com.example.stock.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stock.core.domain.DividendStatistics
import com.example.stock.core.ui.component.toCurrencyString

private val DividendColor = Color(0xFFFFA000) // 與交易類型「現金股息」同色
private val AccentRed = Color(0xFFEF4444)     // 與首頁分頁同色
private val ProfitRed = Color(0xFFE53935)     // 與首頁股票列的損益同色
private val LossGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val dividendState by viewModel.dividendState.collectAsStateWithLifecycle()
    val realizedState by viewModel.realizedState.collectAsStateWithLifecycle()
    val returnState by viewModel.returnState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("股息", "已實現損益", "報酬率")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("統計報表") },
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
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = AccentRed,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTab),
                        color = AccentRed
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
            when (selectedTab) {
                0 -> DividendSection(
                    state = dividendState,
                    onSelectYear = viewModel::selectDividendYear
                )
                1 -> RealizedSection(
                    state = realizedState,
                    onSelectYear = viewModel::selectRealizedYear
                )
                else -> ReturnSection(state = returnState)
            }
        }
    }
}

@Composable
private fun YearChips(years: List<Int>, selectedYear: Int, onSelectYear: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(years, key = { it }) { year ->
            FilterChip(
                selected = year == selectedYear,
                onClick = { onSelectYear(year) },
                label = { Text("$year") }
            )
        }
    }
}

@Composable
private fun EmptyReport(isLoading: Boolean, message: String) {
    if (!isLoading) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(message, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

private fun profitColor(value: Double) = if (value >= 0) ProfitRed else LossGreen

@Composable
private fun RealizedSection(
    state: RealizedUiState,
    onSelectYear: (Int) -> Unit
) {
    val selected = state.selected
    if (selected == null) {
        EmptyReport(state.isLoading, "目前帳戶沒有賣出紀錄")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item { YearChips(state.years.map { it.year }, selected.year, onSelectYear) }

        // 年度總額
        item {
            ReportCard {
                Text("${selected.year} 年已實現損益", fontSize = 14.sp, color = Color.Gray)
                Text(
                    selected.total.toCurrencyString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = profitColor(selected.total)
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryItem("獲利", selected.gain.toCurrencyString(), ProfitRed, Modifier.weight(1f))
                    SummaryItem("虧損", selected.loss.toCurrencyString(), LossGreen, Modifier.weight(1f))
                    SummaryItem("賺錢次數", "${selected.winCount} / ${selected.tradeCount}", Color(0xFF212121), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "歷年合計 ${state.allYearsTotal.toCurrencyString()}　·　成本計算：${state.costBasisLabel}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // 每月長條圖
        item {
            ReportCard {
                Text("每月已實現損益", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                MonthlyBarChart(selected.monthly, colorOf = ::profitColor)
            }
        }

        // 依股票
        item {
            ReportCard {
                Text("依股票", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                selected.byStock.forEachIndexed { index, stock ->
                    if (index > 0) HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stock.name, fontSize = 15.sp)
                            Text(
                                "${stock.symbol}　·　${stock.count} 筆　·　成本 ${stock.cost.toCurrencyString()}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stock.profit.toCurrencyString(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = profitColor(stock.profit)
                            )
                            Text(
                                "${stock.profitPercent.toCurrencyString(2)}%",
                                fontSize = 12.sp,
                                color = profitColor(stock.profit)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** XIRR 顯示文字，例如 +12.34%；無法計算時顯示 — */
private fun Double?.toXirrText(): String {
    if (this == null) return "—"
    val percent = this * 100
    val sign = if (percent >= 0) "+" else ""
    return "$sign${percent.toCurrencyString(2)}%"
}

@Composable
private fun ReturnSection(state: ReturnUiState) {
    val portfolio = state.portfolio
    if (portfolio == null) {
        EmptyReport(state.isLoading, "目前帳戶沒有股票交易紀錄")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        // 整體
        item {
            ReportCard {
                Text("年化報酬率 (XIRR)", fontSize = 14.sp, color = Color.Gray)
                Text(
                    portfolio.xirr.toXirrText(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = portfolio.xirr?.let { profitColor(it) } ?: Color.Gray
                )
                Text("自 ${portfolio.firstDate} 起　·　含股息", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryItem("累計買進", portfolio.invested.toCurrencyString(), Color(0xFF212121), Modifier.weight(1f))
                    SummaryItem("累計取回", portfolio.received.toCurrencyString(), Color(0xFF212121), Modifier.weight(1f))
                    SummaryItem("目前市值", portfolio.currentValue.toCurrencyString(), Color(0xFF212121), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryItem("總損益", portfolio.profit.toCurrencyString(), profitColor(portfolio.profit), Modifier.weight(1f))
                    SummaryItem(
                        "總損益率 (未年化)",
                        "${portfolio.profitPercent.toCurrencyString(2)}%",
                        profitColor(portfolio.profit),
                        Modifier.weight(2f)
                    )
                }
            }
        }

        // 說明
        item {
            Text(
                "XIRR 依每筆買進、賣出、股息的日期計算，持股中的股票以目前市值當作今天取回。" +
                    "持有期間越短，年化後的數字波動越大。股價使用首頁最後一次更新的報價，入金、出金不列入計算。",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // 依股票
        item {
            ReportCard {
                Text("依股票", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                portfolio.stocks.forEachIndexed { index, stock ->
                    if (index > 0) HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stock.name, fontSize = 15.sp)
                            Text(
                                "${stock.symbol}　·　${if (stock.isActive) "持股中" else "已清倉"}　·　自 ${stock.firstDate}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stock.xirr.toXirrText(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = stock.xirr?.let { profitColor(it) } ?: Color.Gray
                            )
                            Text(
                                stock.profit.toCurrencyString(),
                                fontSize = 12.sp,
                                color = profitColor(stock.profit)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun DividendSection(
    state: DividendUiState,
    onSelectYear: (Int) -> Unit
) {
    val selected = state.selected
    if (selected == null) {
        EmptyReport(state.isLoading, "目前帳戶沒有現金股息紀錄")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item { YearChips(state.years.map { it.year }, selected.year, onSelectYear) }

        // 年度總額
        item {
            ReportCard {
                Text("${selected.year} 年實收股息", fontSize = 14.sp, color = Color.Gray)
                Text(
                    selected.total.toCurrencyString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DividendColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "月平均 ${(selected.total / 12).toCurrencyString()}　·　歷年合計 ${state.allYearsTotal.toCurrencyString()}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // 每月長條圖
        item {
            ReportCard {
                Text("每月股息", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                MonthlyBarChart(selected.monthly, colorOf = { DividendColor })
            }
        }

        // 依股票
        item {
            ReportCard {
                Text("依股票", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                selected.byStock.forEachIndexed { index, stock ->
                    if (index > 0) HorizontalDivider(color = Color(0xFFF1F5F9))
                    StockDividendRow(stock, selected.total)
                }
            }
        }
    }
}

@Composable
private fun ReportCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * 每月長條圖，支援負數：有負數時以中間為 0 軸，正數往上、負數往下
 */
@Composable
private fun MonthlyBarChart(monthly: List<Double>, colorOf: (Double) -> Color) {
    val maxPositive = monthly.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val maxNegative = -(monthly.minOrNull()?.coerceAtMost(0.0) ?: 0.0)
    val range = (maxPositive + maxNegative).takeIf { it > 0 } ?: 1.0
    // 正數區與負數區依各自最大值分配高度
    val positiveWeight = (maxPositive / range).toFloat()
    val negativeWeight = (maxNegative / range).toFloat()

    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        monthly.forEachIndexed { index, amount ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (positiveWeight > 0f) {
                        Box(
                            modifier = Modifier.weight(positiveWeight).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (amount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((amount / maxPositive).toFloat().coerceAtLeast(0.02f))
                                        .background(colorOf(amount), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                            }
                        }
                    }
                    if (negativeWeight > 0f) {
                        Box(
                            modifier = Modifier.weight(negativeWeight).fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (amount < 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((-amount / maxNegative).toFloat().coerceAtLeast(0.02f))
                                        .background(colorOf(amount), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                )
                            }
                        }
                    }
                }
                Text("${index + 1}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // 有金額的月份列出數字，長條圖只看得出相對大小
    val nonZeroMonths = monthly.withIndex().filter { it.value != 0.0 }
    if (nonZeroMonths.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        nonZeroMonths.forEach { (index, amount) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${index + 1} 月", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                Text(amount.toCurrencyString(), fontSize = 13.sp, color = colorOf(amount))
            }
        }
    }
}

@Composable
private fun StockDividendRow(stock: DividendStatistics.StockDividend, yearTotal: Double) {
    val percent = if (yearTotal > 0) stock.amount / yearTotal * 100 else 0.0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.name, fontSize = 15.sp)
            Text("${stock.symbol}　·　領息 ${stock.count} 次", fontSize = 12.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stock.amount.toCurrencyString(), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${percent.toCurrencyString(1)}%", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
