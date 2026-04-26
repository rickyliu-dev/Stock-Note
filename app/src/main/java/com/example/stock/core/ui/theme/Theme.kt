package com.example.stock.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ----------------------
// 1. 定義顏色 (Color Palette)
// ----------------------
// 亮色系常用
val Slate800 = Color(0xFF1E293B) // 深藍灰 (主色)
val Red500 = Color(0xFFEF4444)   // 紅色 (強調)
val Slate50 = Color(0xFFF8FAFC)  // 淺灰白 (背景)

// 深色系常用 (Dark Mode)
val Slate200 = Color(0xFFE2E8F0) // 淺灰 (深色模式的主字色)
val Red300 = Color(0xFFFCA5A5)   // 淺紅 (深色模式的強調色，通常會比亮色版淡一點)
val Slate900 = Color(0xFF0F172A) //以此作為深色背景

// ----------------------
// 2. 定義配色表 (Color Schemes)
// ----------------------

// ☀️ 亮色模式配色
private val LightColorScheme = lightColorScheme(
    primary = Slate800,
    onPrimary = Color.White,
    secondary = Red500,
    onSecondary = Color.White,
    background = Slate50,
    surface = Color.White,
    onSurface = Slate800
)

// 🌙 深色模式配色
private val DarkColorScheme = darkColorScheme(
    primary = Slate200,          // 深色模式下，主色通常會變亮，為了對比度
    onPrimary = Slate900,
    secondary = Red300,
    onSecondary = Slate900,
    background = Slate900,       // 背景變深
    surface = Color(0xFF1E293B), // 卡片顏色稍微比背景亮一點
    onSurface = Slate200
)

// ----------------------
// 3. 主題入口函式 (Theme Composable)
// ----------------------
@Composable
fun StockLedgerTheme(
    // 這裡加了一個參數，預設會抓系統設定 (是否開啟深色模式)
    darkTheme: Boolean = false,//isSystemInDarkTheme(),
    // Android 12+ 的動態取色 (通常設為 false 以維持品牌色)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme // 如果是深色模式，用深色表
        else -> LightColorScheme     // 否則用亮色表
    }

    // 設定狀態列 (Status Bar) 顏色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // 狀態列跟隨 Primary 顏色，或是直接設透明/自訂
            if (Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
            }
            // 如果是深色背景，狀態列文字要是亮的；反之亦然
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}