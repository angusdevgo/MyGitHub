package com.mygithub.lab.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 悬浮底栏导航项数据模型（无状态组件，外部驱动）
 */
data class KomiNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * KomiFloatingBottomBar — 高性能悬浮底部导航栏
 *
 * 核心特性：
 * 1. 图层解耦：Indicator 滑块与内容层 Z 轴分离
 * 2. GPU 纯位移：graphicsLayer translationX 驱动滑块，零布局重组
 * 3. 物理弹簧动画：DampingRatioLowBouncy + StiffnessLow，带果冻惯性回弹
 * 4. 悬浮胶囊：左右/底部留边，微透明底色 + 1dp 边框
 * 5. 无状态：只读 selectedIndex，点击回调外部处理
 */
@Composable
fun KomiFloatingBottomBar(
    items: List<KomiNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // 磨砂雾面半透明质感：90% 不透明度，完美雾化后方文字避免重叠，同时保留悬浮穿透轮廓
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
    // 滑块高亮色
    indicatorColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    activeTint: Color = MaterialTheme.colorScheme.primary,
    inactiveTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val view = LocalView.current
    val density = LocalDensity.current

    // 每个 Tab 的中心 X 坐标（用于滑块定位）
    val tabPositions = remember { mutableStateListOf<Float>().apply { repeat(items.size) { add(0f) } } }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    // 滑块目标位置
    val targetIndicatorX = tabPositions.getOrNull(selectedIndex) ?: 0f

    // 弹簧动画：低阻尼弹跳，带物理惯性
    val animatedIndicatorX by animateFloatAsState(
        targetValue = targetIndicatorX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicatorOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // ===== 悬浮胶囊主壳（磨砂雾面半透 + 细微光边框 + 浮岛阴影） =====
        Box(
            modifier = Modifier
                .height(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = false
                )
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // ===== 底层滑块指示器（GPU 纯位移，零重组） =====
            if (tabWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = animatedIndicatorX
                        }
                        .size(
                            width = with(density) { (tabWidthPx * 0.82f).toDp() },
                            height = 44.dp
                        )
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }

            // ===== 上层内容（图标 + 文字纵向排列） =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                val itemWidth = coordinates.size.width.toFloat()
                                val indicatorWidth = itemWidth * 0.82f
                                val startOffset = coordinates.positionInParent().x + (itemWidth - indicatorWidth) / 2f
                                // 去抖：仅在值真正变化时写入状态，避免无谓重组
                                if (tabWidthPx != itemWidth) tabWidthPx = itemWidth
                                if (index < tabPositions.size && tabPositions[index] != startOffset) {
                                    tabPositions[index] = startOffset
                                }
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (index != selectedIndex) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onItemSelected(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (item.badgeCount > 0) {
                                        Badge(containerColor = Color(0xFFFF3B30)) {
                                            Text(
                                                text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    tint = if (isSelected) activeTint else inactiveTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) activeTint else inactiveTint,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
