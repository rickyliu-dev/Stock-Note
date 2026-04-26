package com.example.stock.core.ui.component.modifier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 隱藏彩蛋專用 Modifier
 * @param times 需要連續點擊的次數 (預設 7 次)
 * @param durationMillis 必須在幾毫秒內點完 (預設 3000 毫秒 = 3 秒)
 * @param onSecretTriggered 成功觸發時執行的動作
 */
fun Modifier.secretClick(
    times: Int = 7,
    durationMillis: Long = 3000L,
    onSecretTriggered: () -> Unit
): Modifier = composed {
    var clickCount by remember { mutableIntStateOf(0) }
    var firstClickTime by remember { mutableLongStateOf(0L) }

    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null // 🟢 關鍵：隱藏點擊水波紋，達到真正的「隱藏」
    ) {
        val now = System.currentTimeMillis()

        // 如果距離第一次點擊已經超過設定的時間，就重新計算
        if (now - firstClickTime > durationMillis) {
            clickCount = 1
            firstClickTime = now
        } else {
            clickCount++
            // 達到指定次數，觸發彩蛋並歸零
            if (clickCount >= times) {
                onSecretTriggered()
                clickCount = 0
            }
        }
    }
}