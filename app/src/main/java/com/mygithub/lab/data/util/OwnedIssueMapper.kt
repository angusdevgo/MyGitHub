package com.mygithub.lab.data.util

import com.mygithub.lab.data.api.GitHubIssue
import com.mygithub.lab.data.model.OwnedIssueItem

/**
 * Issue → OwnedIssueItem 纯映射与排序工具（便于单元测试）
 */
object OwnedIssueMapper {

    /**
     * 将单个仓库的 issue 列表映射为统一展示模型
     * @param repoFullName 仓库全名 owner/repo
     */
    fun map(repoFullName: String, issues: List<GitHubIssue>): List<OwnedIssueItem> =
        issues.map { issue ->
            OwnedIssueItem(
                repoFullName = repoFullName,
                number = issue.number,
                title = issue.title,
                state = issue.state,
                isPr = issue.isPr,
                isMerged = issue.isMerged,
                comments = issue.comments,
                updatedAt = issue.updated_at,
                htmlUrl = issue.html_url
            )
        }

    /**
     * 去重（按 cacheKey）并按 updatedAt 降序排序
     */
    fun curate(items: List<OwnedIssueItem>): List<OwnedIssueItem> =
        items
            .filter { it.repoFullName.isNotBlank() && it.number > 0 }
            .distinctBy { it.cacheKey }
            .sortedByDescending { it.updatedAt }
}
