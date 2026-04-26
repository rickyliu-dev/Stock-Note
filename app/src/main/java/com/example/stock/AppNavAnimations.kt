package com.example.stock

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

object AppNavAnimations {
    private const val DURATION = 250 // 統一管理時間

    // 進場：從右往左滑
    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(DURATION)
        ) + fadeIn(animationSpec = tween(DURATION))
//        EnterTransition.None
    }

    // 出場：往左滑出
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(DURATION)
        ) + fadeOut(animationSpec = tween(DURATION))
//        ExitTransition.None
    }

    // 返回鍵進場：從左往右滑回來
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(DURATION)
        ) + fadeIn(animationSpec = tween(DURATION))
//        EnterTransition.None
    }

    // 返回鍵出場：往右滑消失
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(DURATION)
        ) + fadeOut(animationSpec = tween(DURATION))
//        ExitTransition.None
    }
}