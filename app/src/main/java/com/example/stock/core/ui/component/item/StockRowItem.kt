package com.example.stock.core.ui.component.item

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stock.core.ui.component.toCurrencyString

@Composable
fun StockRowItem(
    name: String,
    shares: Double,
    currentPrice: Double,
    change: Double,
    changePercent: Double,
    avgCost: Double,
    profit: Double,
    profitPercent: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    subtitle: String? = null
) {
    val isCash = name.contains("現金")
    
    // 定義顏色：台股慣例紅漲綠跌
    val profitColor = if (profit >= 0) {
        if (isCash) Color.Black else Color(0xFFE53935)
    } else Color(0xFF43A047)
    
    val textColor = Color(0xFF212121)

    val isUp = change > 0
    val isDown = change < 0

    val changeColor = when {
        isUp -> Color(0xFFE53935) // 紅色
        isDown -> Color(0xFF43A047) // 綠色
        else -> MaterialTheme.colorScheme.onSurface // 平盤預設色
    }

    val sign = if (isUp) "+" else ""

    val formattedChange = change.toCurrencyString(2)
    val formattedPercent = changePercent.toCurrencyString(2)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp)// 增加內距讓手指好點
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 股票/股數
            Column(modifier = Modifier.weight(1.2f)) {
                Text(text = name, fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
                val displaySubtitle = subtitle ?: if (!isCash) {
                    "${shares.toCurrencyString(0)}股"
                } else {
                    "查看收支明細"
                }
                Text(text = displaySubtitle, color = Color.Gray, fontSize = 11.sp)
            }

            // 股價
            Column(
                modifier = Modifier.weight(1f), 
                horizontalAlignment = Alignment.End
            ) {
                if (!isCash) {
                    Text(
                        text = currentPrice.toCurrencyString(2),
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 15.sp, // 稍微縮小 16->15
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$sign$formattedChange",
                            color = changeColor,
                            fontSize = 10.sp, // 稍微縮小 11->10
                            fontWeight = FontWeight.Medium,
                            lineHeight = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "($sign$formattedPercent%)",
                            color = changeColor,
                            fontSize = 10.sp, // 稍微縮小 11->10
                            fontWeight = FontWeight.Medium,
                            lineHeight = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // 成本均價
            Text(
                text = if (isCash) "" else avgCost.toCurrencyString(2),
                modifier = Modifier.weight(0.9f), // 權重從 1 稍微縮減
                textAlign = TextAlign.End,
                fontSize = 14.sp, // 稍微縮小 15->14
                color = textColor,
                maxLines = 1
            )

            // 總損益 (現金則顯示餘額)
            Column(
                modifier = Modifier.weight(1.1f), 
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = profit.toCurrencyString(0),
                    color = profitColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, // 稍微縮小 16->15
                    maxLines = 1
                )
                if (!isCash) {
                    Text(
                        text = "${if (profitPercent > 0) "+" else ""}${profitPercent.toCurrencyString(2)}%",
                        color = profitColor,
                        fontSize = 10.sp, // 稍微縮小 11->10
                        maxLines = 1
                    )
                }
            }
        }
        // 分隔線
        HorizontalDivider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.2f))
    }
}