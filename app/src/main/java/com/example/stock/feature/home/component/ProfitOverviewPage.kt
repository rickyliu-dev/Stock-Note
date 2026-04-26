package com.example.stock.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stock.core.ui.component.toCurrencyString
import com.example.stock.function.scale

@Composable
fun ProfitOverviewPage(
    totalAssets: Double,
    cashBalance: Double,
    totalProfit: Double,
    dailyProfit: Double,
    isCumulative: Boolean,
    onToggleMode: () -> Unit,
    includeDividends: Boolean,
    onToggleDividends: (Boolean) -> Unit,
    isCashEnabled: Boolean = false
) {
    // 這裡直接使用您原本寫好的內容
    val profitColor = if (totalProfit >= 0) Color(0xFFE53935) else Color(0xFF43A047)
    val formattedTotalAssets = totalAssets.toCurrencyString(0)
    val formattedCashBalance = cashBalance.toCurrencyString(0)
    val dailyProfitColor = when {
        dailyProfit > 0 -> Color(0xFFE53935)
        dailyProfit < 0 -> Color(0xFF43A047)
        else -> MaterialTheme.colorScheme.onSurface // 平盤 (0)
    }
    // 損益顯示
    val prefix = if (dailyProfit > 0) "+" else ""
    val formattedProfit = dailyProfit.toCurrencyString(0)

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        Column {
            // 上排：標題切換與含息開關
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.clickable { onToggleMode() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCumulative) "累積損益" else "庫存損益",
                        color = Color.Gray,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch",
                        modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        tint = Color.Gray
                    )
                }

                // 含息開關 (使用 Switch 或 Checkbox)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("含息", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Switch(
                        checked = includeDividends,
                        onCheckedChange = onToggleDividends,
                        modifier = Modifier.scale(0.8f) // 縮小一點比較精緻
                    )
                }
            }

            // 下排：大數字顯示
            Text(
                text = "$ ${totalProfit.toCurrencyString(0)}",
                color = profitColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左邊：持股日損益
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "持股日損益",
                    color = Color.Gray,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "$prefix$formattedProfit",
                    color = dailyProfitColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // 右邊：總資產
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "總資產",
                    color = Color.Gray,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$ $formattedTotalAssets",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
