package com.mygithub.lab.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 极客高质感下拉刷新指示器：
 * 1. 下拉时：随距离平滑缩放、旋转
 * 2. 刷新时：GPU 硬件加速无限 360° 旋转，绝不卡滞停顿
 * 3. 磨砂微光圆盘底座，完美契合暗黑/明亮双模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.KomiPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = state.distanceFraction.coerceIn(0f, 1f)

    // 无限平滑旋转动画（750ms 一圈，线性平滑无顿挫）
    val infiniteTransition = rememberInfiniteTransition(label = "pull_refresh_rotation")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    if (progress > 0f || isRefreshing) {
        val currentScale = if (isRefreshing) 1f else progress
        val currentAlpha = if (isRefreshing) 1f else progress.coerceIn(0.2f, 1f)
        val currentRotation = if (isRefreshing) spinningRotation else (progress * 180f)

        Box(
            modifier = modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = (progress * 72.dp.toPx()).coerceAtMost(96.dp.toPx())
                    scaleX = currentScale
                    scaleY = currentScale
                    alpha = currentAlpha
                }
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                // 正在刷新：动态进度环 + 核心旋转图标
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp
                )
            } else {
                // 下拉拖拽阶段：随手势旋转的图标
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(currentRotation)
                )
            }
        }
    }
}
