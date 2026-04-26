package com.example.stock.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AssetLineChartPage(
    historyData: List<Pair<Long, Double>>,
    onRangeSelected: (String) -> Unit
) {
    var selectedRange by remember { mutableStateOf("1M") }
    val ranges = listOf("1W", "1M", "1Y", "ALL")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 1. 時間範圍選擇器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ranges.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = {
                        selectedRange = range
                        onRangeSelected(range)
                    },
                    label = { Text(range) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 折線圖繪製區 (Canvas)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (historyData.isEmpty()) {
                Text("暫無歷史數據", color = Color.Gray)
            } else {
                // 這裡簡單畫一條線示意
                // 實際專案建議使用 Vico Library
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val path = androidx.compose.ui.graphics.Path()
                    val width = size.width
                    val height = size.height

                    // 找出數據的最大最小值以計算比例
                    val maxVal = historyData.maxOf { it.second }
                    val minVal = historyData.minOf { it.second }
                    val range = maxVal - minVal

                    historyData.forEachIndexed { index, pair ->
                        val x = (index.toFloat() / (historyData.size - 1)) * width
                        // Y 軸反轉 (0 在上面)
                        val y = height - ((pair.second - minVal) / range * height).toFloat()

                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF2196F3),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                    )
                }
            }
        }
    }
}