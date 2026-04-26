package com.example.stock.feature.detail.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.ui.component.toCurrencyString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItemCard(
    transaction: TransactionItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var col1Title = "成交單價"
    var col1Value = "$ ${transaction.price.toCurrencyString(2)}"
    var col2Title = "成交股數"
    var col2Value = "${transaction.shares} 股"

    var totalTitle = if (transaction.type == TransactionType.BUY) "總支出" else "總收入"
    var totalValue = "$ ${transaction.total.toCurrencyString(0)}"

    when (transaction.type) {
        TransactionType.DIVIDEND -> {
            col1Title = "單股配息"
            col1Value = if (transaction.price > 0) "$ ${transaction.price.toCurrencyString(2)}" else "-"

            col2Title = "參與股數"
            col2Value = if (transaction.shares > 0) "${transaction.shares} 股" else "-"

            totalTitle = "實收現金股息"
            totalValue = "$ ${transaction.dividend.toCurrencyString(0)}"
        }

        TransactionType.STOCK_DIVIDEND -> {
            col1Title = "配股率"
            col1Value = if (transaction.price > 0) "${transaction.price}" else "-"

            col2Title = "參與除權"
            col2Value = if (transaction.participatingShares > 0) "${transaction.participatingShares} 股" else "-"

            totalTitle = "總配發股數"
            totalValue = "${transaction.shares} 股"
        }

        TransactionType.DEPOSIT -> {
            col1Title = "-"
            col1Value = "-"
            col2Title = "-"
            col2Value = "-"
            totalTitle = "存入金額"
            totalValue = "$ ${transaction.total.toCurrencyString(0)}"
        }

        TransactionType.WITHDRAW -> {
            col1Title = "-"
            col1Value = "-"
            col2Title = "-"
            col2Value = "-"
            totalTitle = "提出金額"
            totalValue = "$ ${transaction.total.toCurrencyString(0)}"
        }

        else -> {
            // 買賣 (BUY, SELL) 維持上面的預設值，什麼都不用做！
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    color = transaction.type.getColor().copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = transaction.type.label,
                        color = transaction.type.getColor(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(transaction.date.toString(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailColumn(
                    title = col1Title,
                    value = col1Value
                )
                DetailColumn(
                    title = col2Title,
                    value = col2Value,
                    alignment = Alignment.CenterHorizontally
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color.LightGray)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = totalTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
                Text(
                    text = totalValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = transaction.type.getColor()
                )
            }

            if (transaction.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("📝 ${transaction.note}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DetailColumn(title: String, value: String, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = alignment) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}