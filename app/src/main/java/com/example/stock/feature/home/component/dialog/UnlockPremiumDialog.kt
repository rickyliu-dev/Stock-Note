package com.example.stock.feature.home.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.window.DialogProperties
import com.example.stock.BuildConfig

@Composable
fun UnlockPremiumDialog(
    onDismiss: () -> Unit,
    // 傳入一個驗證函式，回傳 Boolean 代表是否解鎖成功
    onVerify: (String) -> Boolean
) {
    // 只有在 DEBUG 模式下才允許顯示，這是一個額外的安全防線
    if (!BuildConfig.DEBUG) {
        onDismiss()
        return
    }

    var codeInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* 點擊外部不消失：這裡不做事 */ },
        properties = DialogProperties(
            dismissOnBackPress = true, // 允許返回鍵關閉
            dismissOnClickOutside = false // 🚫 關鍵：點擊外部不會消失
        ),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "解鎖高級功能",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "請輸入開發者提供的專屬授權碼，以解鎖無限制的進階版功能。",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        // 🟢 自動轉大寫，並過濾掉空白，提升使用者體驗
                        codeInput = it.uppercase().replace(" ", "")
                        isError = false // 只要使用者重新打字，就清除紅字錯誤狀態
                    },
                    label = { Text("授權碼") },
                    placeholder = { Text("例如：VIP-8888") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("授權碼錯誤或已失效", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (codeInput.isNotBlank()) {
                                val success = onVerify(codeInput)
                                if (success) onDismiss() else isError = true
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (codeInput.isNotBlank()) {
                        val success = onVerify(codeInput)
                        // 🟢 成功才關閉 Dialog，失敗則亮紅燈讓使用者重試
                        if (success) {
                            onDismiss()
                        } else {
                            isError = true
                        }
                    } else {
                        isError = true
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("驗證並解鎖")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.Gray)
            }
        }
    )
}