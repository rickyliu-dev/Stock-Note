package com.example.stock.core.data.model

// 首頁股票清單的排序依據 (DEFAULT 維持原本的順序)
enum class HomeSortOrder(val label: String) {
    DEFAULT("預設"),
    MARKET_VALUE("市值"),
    PROFIT("損益"),
    PROFIT_PERCENT("損益率");
}

data class HomeSort(
    val order: HomeSortOrder = HomeSortOrder.DEFAULT,
    val descending: Boolean = true
)
