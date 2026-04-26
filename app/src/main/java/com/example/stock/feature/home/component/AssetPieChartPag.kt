package com.example.stock.feature.home.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stock.core.ui.component.toCurrencyString
import kotlin.math.cos
import kotlin.math.sin

// 定義顏色清單
val chartColors = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFA726),
    Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFF26C6DA),
    Color(0xFFFF7043), Color(0xFF8D6E63), Color(0xFF78909C),
    Color(0xFFD4E157), Color(0xFFBDBDBD) // 最後一個灰色給 "其他" 用
)

@Composable
fun AssetPieChartPage(
    data: Map<String, Double>
) {
    val totalValue = data.values.sum()

    // --- 1. 資料預處理邏輯 (Top 10 + Others) ---
    // 使用 remember 確保只有數據變動時才重新計算，節省效能
    val displayedData = remember(data) {
        if (data.isEmpty()) return@remember emptyList<Pair<String, Double>>()

        // A. 先排序 (由大到小)
        val sortedList = data.entries.sortedByDescending { it.value }

        // B. 切分數據
        if (sortedList.size <= 10) {
            // 如果少於等於 10 筆，直接轉成 Pair List
            sortedList.map { it.toPair() }
        } else {
            // 如果超過 10 筆：
            // 取前 9 名
            val top9 = sortedList.take(9).map { it.toPair() }
            // 第 10 名以後全部加總
            val othersValue = sortedList.drop(9).sumOf { it.value }
            // 合併
            top9 + ("其他" to othersValue)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 左邊：圓餅圖 (逆時針 + 分隔線) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Text("資產分佈", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (totalValue <= 0) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        style = Stroke(width = 40f)
                    )
                }
                Text("尚無資產", color = Color.Gray)
            } else {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    // 設定半徑 (畫分隔線會用到)
                    val radius = size.minDimension / 2
                    val centerOffset = center

                    // 起始角度：-90度 (12點鐘方向)
                    var currentAngle = -90f

                    displayedData.forEachIndexed { index, (symbol, value) ->
                        // 計算扇形角度 (負值代表逆時針)
                        val sweepAngle = -((value / totalValue) * 360).toFloat()

                        // 決定顏色：如果是 "其他"，固定用灰色，不然就循環取色
                        val color = if (symbol == "其他") Color.LightGray else chartColors[index % (chartColors.size - 1)]

                        // A. 畫扇形
                        drawArc(
                            color = color,
                            startAngle = currentAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )

                        // B. 畫黑色分隔線 (只在有兩個以上區塊時才畫)
                        if (displayedData.size > 1) {
                            // 計算線條終點座標 (三角函數：x = r*cos, y = r*sin)
                            // 注意：角度要轉成「弧度 (Radians)」才能算
                            val angleInRad = (currentAngle * Math.PI / 180).toFloat()
                            val endX = centerOffset.x + radius * cos(angleInRad)
                            val endY = centerOffset.y + radius * sin(angleInRad)

                            drawLine(
                                color = Color.Black,
                                start = centerOffset,
                                end = Offset(endX, endY),
                                strokeWidth = 3f, // 線條粗細
                                cap = StrokeCap.Round
                            )
                        }

                        // 更新角度 (往下一個位置移動)
                        currentAngle += sweepAngle
                    }

                    // 補畫最後一條分隔線 (封口)
                    if (displayedData.size > 1) {
                        val angleInRad = (currentAngle * Math.PI / 180).toFloat()
                        val endX = centerOffset.x + radius * cos(angleInRad)
                        val endY = centerOffset.y + radius * sin(angleInRad)
                        drawLine(
                            color = Color.Black,
                            start = centerOffset,
                            end = Offset(endX, endY),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }

        // --- 右邊：圖例 (顯示處理過後的 Top 10 + 其他) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            if (totalValue > 0) {
                displayedData.forEachIndexed { index, (symbol, value) ->
                    val color = if (symbol == "其他") Color.LightGray else chartColors[index % (chartColors.size - 1)]
                    val percentage = (value / totalValue) * 100

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = symbol,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${percentage.toCurrencyString(1)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}