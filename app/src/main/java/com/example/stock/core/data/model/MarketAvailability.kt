package com.example.stock.core.data.model

/**
 * 這個 flavor 允許搜尋的市場地區，由各 flavor 提供實作：
 * basic 只有 {TW}；personal 有 {TW, US, ALL}。
 */
interface MarketAvailability {
    val searchRegions: Set<SearchRegion>
}
