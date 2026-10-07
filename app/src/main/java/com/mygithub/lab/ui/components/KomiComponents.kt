package com.mygithub.lab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * KomiSurface — komi-store 血统的硬阴影卡片容器
 * 圆角 12dp + 1px outlineVariant 描边 + 硬阴影（不弥散）
 * 可选 onClick：ripple 水波纹裁剪为 12dp 圆角（不再方格）
 */
@Composable
fun KomiSurface(
    modifier: Modifier = Modifier,
    elevation: KomiSurfaceElevation = KomiSurfaceElevation.Level1,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier
        .shadow(
            elevation = 0.dp, // 不用 M3 弥散阴影
            shape = shape
        )
        .background(MaterialTheme.colorScheme.surfaceContainer, shape)
        .border(
            width = if (elevation == KomiSurfaceElevation.High) 1.dp else 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = shape
        )
    if (onClick != null) {
        androidx.compose.foundation.layout.Box(
            modifier = base
                .clip(shape) // ripple 裁剪为圆角，跟随卡片轮廓
                .clickable(onClick = onClick)
        ) {
            content()
        }
    } else {
        androidx.compose.foundation.layout.Box(modifier = base) {
            content()
        }
    }
}

enum class KomiSurfaceElevation { Level1, High }

/** 硬阴影占位（保留接口，后续实现 komi-store PersonalityShadow 偏移实心阴影） */

/**
 * KomiChip — 胶囊芯片（选中态填充 NORD 蓝，未选中描边）
 * ripple 裁剪为胶囊形状，消除方格水波纹
 */
@Composable
fun KomiChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(50)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .shadow(0.dp, shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer,
                shape
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clip(shape) // ripple 裁剪为胶囊，跟随轮廓
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 语言色点（GitHub 官方语言色）
 */
val LanguageColors = mapOf(
    "Kotlin" to Color(0xFFA97BFF),
    "Java" to Color(0xFFb07219),
    "Python" to Color(0xFF3572A5),
    "JavaScript" to Color(0xFFf1e05a),
    "TypeScript" to Color(0xFF3178c6),
    "Go" to Color(0xFF00ADD8),
    "Rust" to Color(0xFFdea584),
    "C" to Color(0xFF555555),
    "C++" to Color(0xFFf34b7d),
    "C#" to Color(0xFF178600),
    "Swift" to Color(0xFFF05138),
    "Dart" to Color(0xFF00B4AB),
    "Vue" to Color(0xFF41b883),
    "Shell" to Color(0xFF89e051),
    "Ruby" to Color(0xFF701516),
    "PHP" to Color(0xFF4F5D95)
)

fun languageColor(name: String?): Color =
    LanguageColors[name] ?: Color(0xFF81A1C1)

/**
 * KomiRepoCard — 仓库卡片（owner/名称/描述/语言色点/星数）
 */
@Composable
fun KomiRepoCard(
    owner: String,
    name: String,
    description: String,
    language: String?,
    stars: Int,
    forks: Int = 0,
    updatedAt: String? = null,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "cardScale"
    )
    KomiSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = onClick // ripple 由 KomiSurface 裁剪为 12dp 圆角，跟随卡片轮廓
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$owner / ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!badge.isNullOrBlank()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            // 属性栏：语言标签 + Star + Fork + 更新时间
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言类型（精致微光小胶囊）
                if (!language.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(languageColor(language).copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(languageColor(language), CircleShape)
                        )
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Star 标星（矢量小金星 + 数量）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Stars",
                        tint = Color(0xFFEBCB8B), // 经典温暖金星色
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = formatStars(stars),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Fork 复刻（矢量分支分支图标 + 数量）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallSplit,
                        contentDescription = "Forks",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatStars(forks),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (updatedAt != null) {
                val timeText = remember(updatedAt) { relativeTimeFromIso(updatedAt) }
                Text(
                    text = "更新于 $timeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun formatStars(count: Int): String = when {
    count >= 10000 -> String.format("%.1fw", count / 10000f)
    count >= 1000 -> String.format("%.1fk", count / 1000f)
    else -> count.toString()
}

fun relativeTimeFromIso(iso: String): String {
    return try {
        // 例 2026-10-06T08:30:00Z
        val cleaned = iso.replace("Z", "+00:00")
        val then = java.time.OffsetDateTime.parse(cleaned).toInstant()
        val now = java.time.Instant.now()
        val sec = java.time.Duration.between(then, now).seconds
        when {
            sec < 60 -> "刚刚"
            sec < 3600 -> "${sec / 60}分钟前"
            sec < 86400 -> "${sec / 3600}小时前"
            sec < 604800 -> "${sec / 86400}天前"
            sec < 2592000 -> "${sec / 604800}周前"
            sec < 31536000 -> "${sec / 2592000}个月前"
            else -> "${sec / 31536000}年前"
        }
    } catch (_: Exception) { iso }
}
