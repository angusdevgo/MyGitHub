package com.mygithub.lab.data.model

import kotlinx.serialization.Serializable

/**
 * 「议题」分段统一展示模型：自有仓库的 Issue / Pull Request
 */
@Serializable
data class OwnedIssueItem(
    val repoFullName: String,   // owner/repo
    val number: Int,
    val title: String,
    val state: String,          // open | closed
    val isPr: Boolean,
    val isMerged: Boolean,
    val comments: Int,
    val updatedAt: String,
    val htmlUrl: String
) {
    /** Room 缓存主键：owner/repo#42（与 received_activity / received_counters 等 category 不冲突） */
    val cacheKey: String get() = "$repoFullName#$number"

    /** 用于状态胶囊展示：merged > closed > open */
    val displayState: String
        get() = when {
            isMerged -> "merged"
            state.equals("closed", ignoreCase = true) -> "closed"
            else -> "open"
        }
}

/**
 * 自有仓库总量概览（用于「动态」Tab 顶部卡片）
 */
data class OwnedRepoSummary(
    val totalStars: Int = 0,
    val totalForks: Int = 0,
    val totalOpenIssues: Int = 0,
    val repoCount: Int = 0,
    val starsDelta: Int = 0,
    val forksDelta: Int = 0
)
