package com.example.stock.core.data.model

import com.example.stock.core.data.enumClass.DashboardIds
import kotlinx.serialization.Serializable

@Serializable
data class DashboardSettingItem(
    val id: DashboardIds,
    val title: String,
    val isVisible: Boolean = true
)