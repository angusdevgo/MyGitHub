package com.mygithub.lab.data.util

import com.mygithub.lab.data.api.GitHubEvent

/**
 * 纯数据合并 / 筛选工具（便于单元测试）
 */
object DataMergeUtil {

    /**
     * 事件流筛选：仅保留白名单类型 → 按 id 去重 → 按 created_at 降序
     */
    fun curateEvents(
        events: List<GitHubEvent>,
        allowedTypes: Set<String>
    ): List<GitHubEvent> {
        return events
            .filter { allowedTypes.contains(it.type) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.created_at }
    }
}
