package com.example.stock.feature.hedge

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.stock.core.ui.component.screen.PlaceholderScreen
import com.example.stock.navigation.FeatureEntry
import javax.inject.Inject

/**
 * 避險功能的首頁入口（只有 personal 有）。
 * 目前只是建置中頁面，由 HedgeModule 用 @IntoSet 放進 Set<FeatureEntry>。
 */
class HedgeFeatureEntry @Inject constructor() : FeatureEntry {
    override val title = "避險（建置中）"
    override val icon: ImageVector = Icons.Default.Shield
    override val route = ROUTE

    override fun NavGraphBuilder.register(navController: NavHostController) {
        // 這裡不能寫 route：在 NavGraphBuilder 的 extension 中會解析成 NavGraphBuilder.route（String?）
        composable(route = ROUTE) {
            PlaceholderScreen(
                title = "避險",
                onBack = { navController.popBackStack() }
            )
        }
    }

    private companion object {
        const val ROUTE = "hedge"
    }
}
