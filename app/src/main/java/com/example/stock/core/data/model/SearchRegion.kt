package com.example.stock.core.data.model

enum class SearchRegion {
    TW,     // 僅台股 (只查本地清單)
    US,     // 僅美股 (只查 Yahoo)
    ALL     // 全部 (混合搜尋)
}