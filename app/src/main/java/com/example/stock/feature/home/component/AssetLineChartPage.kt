package com.example.stock.feature.home.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stock.core.data.model.AssetHistoryRange
import com.example.stock.core.data.model.AssetSnapshot
import com.example.stock.core.ui.component.toCurrencyString

private val LineColor = Color(0xFF2196F3)

@Composable
fun AssetLineChartPage(
    historyData: List<AssetSnapshot>,
    selectedRange: AssetHistoryRange,
    onRangeSelected: (AssetHistoryRange) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 1. 時間範圍選擇器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AssetHistoryRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onRangeSelected(range) },
                    label = { Text(range.label) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        // 2. 期間變化
        if (historyData.size >= 2) {
            val first = historyData.first()
            val last = historyData.last()
            val change = last.totalAssets - first.totalAssets
            val percent = if (first.totalAssets > 0) change / first.totalAssets * 100 else 0.0
            val sign = if (change >= 0) "+" else ""
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${first.date.substring(5)} ~ ${last.date.substring(5)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$sign${change.toCurrencyString()} ($sign${percent.toCurrencyString(2)}%)",
                    fontSize = 12.sp,
                    color = if (change >= 0) Color(0xFFE53935) else Color(0xFF43A047)
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }

        // 3. 折線圖
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp)
                .background(Color(0xFFF8F8F8), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (historyData.size < 2) {
                Text(
                    "每天開啟 App 時會記錄一次總資產\n累積兩天以上就會畫出走勢",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                AssetLineChart(historyData.map { it.totalAssets })
            }
        }
    }
}

@Composable
private fun AssetLineChart(values: List<Double>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val maxVal = values.max()
        val minVal = values.min()
        // 數值完全相同時畫在正中間
        val range = (maxVal - minVal).takeIf { it > 0 }
        val stepX = size.width / (values.size - 1)

        val points = values.mapIndexed { index, value ->
            val ratio = if (range != null) ((value - minVal) / range).toFloat() else 0.5f
            Offset(index * stepX, size.height - ratio * size.height)
        }

        val linePath = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(LineColor.copy(alpha = 0.25f), Color.Transparent))
        )
        drawPath(
            path = linePath,
            color = LineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(LineColor, radius = 6f, center = points.last())
    }
}
